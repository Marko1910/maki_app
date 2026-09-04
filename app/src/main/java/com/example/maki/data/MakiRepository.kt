package com.example.maki.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer

/** High-level data access for the app. */
object MakiRepository {

    // In-memory caches for reference data that is identical for every user and
    // rarely changes — avoids re-fetching catalogs on each navigation.
    private var materialsCache: List<MaterialDto>? = null
    private var optionsCache: List<RedemptionOptionDto>? = null

    /** Temporary test login. Replace with a real login / role-selection screen. */
    suspend fun signInAsTestGenerator(): Session = signIn("generador@maki.test", "Maki12345!")

    /** Temporary rider test login (mirrors [signInAsTestGenerator]). */
    suspend fun signInAsTestRider(): Session = signIn("rider@maki.test", "Maki12345!")

    /** Email + password sign-in; persists the session for the next launch. */
    suspend fun signIn(email: String, password: String): Session =
        MakiApi.signIn(email, password).also {
            MakiPrefs.saveSession(it)
            // When sign-up needed an email confirmation there was no session to write
            // the address with, so it is still sitting in prefs. Land it on the first
            // real login instead of losing what the user typed during onboarding.
            runCatching { claimPendingAddress() }
        }

    private suspend fun claimPendingAddress() {
        val line = MakiPrefs.pendingAddress?.takeIf { it.isNotBlank() } ?: return
        if (loadPrimaryAddress() != null) { MakiPrefs.pendingAddress = null; return }
        savePrimaryAddress(null, line, MakiPrefs.pendingLat, MakiPrefs.pendingLng)
        MakiPrefs.pendingAddress = null
    }

    /**
     * Creates the account for [role] and, when the project signs the user straight
     * in, persists the session and registers [addressLine] as their primary address
     * (with the device fix, so dispatch can measure distance to riders).
     * Returns true when the user is signed in; false when the email needs confirming.
     */
    suspend fun signUp(
        email: String,
        password: String,
        role: String,
        fullName: String,
        phone: String? = null,
        addressLine: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Boolean {
        val session = MakiApi.signUp(email.trim(), password, role, fullName.trim(), phone) ?: return false
        MakiPrefs.saveSession(session)
        if (role == "generador" && !addressLine.isNullOrBlank()) {
            // Best-effort: a failed address must not cost the user their new account.
            runCatching { savePrimaryAddress(null, addressLine, latitude, longitude) }
        }
        return true
    }

    /** Restores a stored session and refreshes its token. True if the user can skip login. */
    suspend fun restoreSession(): Boolean {
        val stored = MakiPrefs.loadSession() ?: return false
        MakiApi.restoreSession(stored)
        val refresh = stored.refreshToken ?: return false
        return try {
            MakiPrefs.saveSession(MakiApi.refresh(refresh))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        MakiApi.signOut()
        MakiPrefs.clearSession()
        materialsCache = null
        optionsCache = null
    }

    private val uid: String? get() = MakiApi.session?.userId

    /** Decodes a PostgREST array response into a list of [T]. */
    private suspend fun <T> list(path: String, serializer: KSerializer<T>): List<T> =
        MakiApi.json.decodeFromString(ListSerializer(serializer), MakiApi.getRaw(path))

    // ---- Home / Profile ----

    /** Loads the signed-in generator's profile, caching it for instant first paint. */
    suspend fun loadGeneratorProfile(): GeneratorProfileDto? {
        val id = uid ?: return null
        val select = "points_balance,cash_earned_total,current_streak_days," +
            "co2_avoided_kg,recycled_kg,water_saved_l,trees_equivalent,profiles(display_name,full_name)"
        val dto = list("generator_profiles?profile_id=eq.$id&select=$select", GeneratorProfileDto.serializer())
            .firstOrNull()
        if (dto != null) {
            MakiPrefs.cachedProfileJson = MakiApi.json.encodeToString(GeneratorProfileDto.serializer(), dto)
        }
        return dto
    }

    /** The last persisted profile, for rendering instantly before the network responds. */
    fun cachedProfile(): GeneratorProfileDto? =
        MakiPrefs.cachedProfileJson?.let {
            runCatching { MakiApi.json.decodeFromString(GeneratorProfileDto.serializer(), it) }.getOrNull()
        }

    // ---- Wallet ----

    suspend fun loadRedemptionOptions(): List<RedemptionOptionDto> = optionsCache ?: list(
        "redemption_options?is_active=eq.true" +
            "&select=id,code,name,kind,description,points_per_unit,min_points,unit_value,icon,color,sort_order" +
            "&order=sort_order.asc",
        RedemptionOptionDto.serializer(),
    ).also { optionsCache = it }

    suspend fun loadRedemptions(): List<RedemptionDto> {
        val id = uid ?: return emptyList()
        return list(
            "redemptions?user_id=eq.$id" +
                "&select=kind,points_spent,monetary_value,status,created_at,redemption_options(name,icon)" +
                "&order=created_at.desc",
            RedemptionDto.serializer(),
        )
    }

    // ---- Rankings ----

    /**
     * The most recent open board for this user's neighborhood (block scope first).
     * [role] scopes it to the caller's own board — generator and rider boards are
     * separate rows in `leaderboard_periods.participant_role`.
     */
    suspend fun loadOpenPeriod(role: String = "generador"): LeaderboardPeriodDto? = list(
        "leaderboard_periods?status=eq.open&participant_role=eq.$role" +
            "&select=id,scope,period_type,block_label,period_start,period_end,status" +
            "&order=period_start.desc&limit=1",
        LeaderboardPeriodDto.serializer(),
    ).firstOrNull()

    suspend fun loadLeaderboardEntries(periodId: String): List<LeaderboardEntryDto> = list(
        "leaderboard_entries?period_id=eq.$periodId" +
            "&select=rank,display_name,points,streak_days,delta,user_id&order=rank.asc",
        LeaderboardEntryDto.serializer(),
    )

    suspend fun loadRankingRewards(scope: String, periodType: String): List<RankingRewardDto> = list(
        "ranking_rewards?is_active=eq.true&scope=eq.$scope&period_type=eq.$periodType" +
            "&select=rank,reward_multiplier,reward_points,description&order=rank.asc",
        RankingRewardDto.serializer(),
    )

    // ---- Pickup form ----

    suspend fun loadMaterials(): List<MaterialDto> = materialsCache ?: list(
        "materials?is_active=eq.true&select=id,code,name,icon,color,points_per_unit,sort_order&order=sort_order.asc",
        MaterialDto.serializer(),
    ).also { materialsCache = it }

    suspend fun loadPrimaryAddress(): AddressDto? {
        val id = uid ?: return null
        return list(
            "addresses?profile_id=eq.$id&select=id,label,address_line,reference,block_label,is_primary,latitude,longitude" +
                "&order=is_primary.desc&limit=1",
            AddressDto.serializer(),
        ).firstOrNull()
    }

    /**
     * Updates the user's primary address, or creates it on first use. Coordinates are
     * only written when we have them — an edit typed by hand must not erase the fix
     * dispatch relies on.
     */
    suspend fun savePrimaryAddress(
        addressId: String?,
        line: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        val id = uid ?: error("No session")
        if (addressId != null) {
            val body = MakiApi.json.encodeToString(
                AddressPatch.serializer(),
                AddressPatch(address_line = line, latitude = latitude, longitude = longitude),
            )
            MakiApi.patch("addresses?id=eq.$addressId", body)
        } else {
            val body = MakiApi.jsonFull.encodeToString(
                NewAddress.serializer(),
                NewAddress(profile_id = id, address_line = line, latitude = latitude, longitude = longitude),
            )
            MakiApi.post("addresses", body, prefer = "return=minimal")
        }
    }

    // ---- Notifications ----

    suspend fun loadNotifications(): List<NotificationDto> {
        val id = uid ?: return emptyList()
        return list(
            "notifications?user_id=eq.$id&select=id,kind,title,body,is_read,created_at&order=created_at.desc",
            NotificationDto.serializer(),
        )
    }

    // ---- Pickups (tracking + history) ----

    private const val PICKUP_SELECT =
        "id,code,status,scheduled_date,window_label,eta_minutes,generator_points_awarded,rider_id," +
            "created_at,completed_at,latitude,longitude,distance_m," +
            "pickup_items(reported_quantity,confirmed_quantity,points,materials(id,name,code,icon))," +
            "rider:profiles!pickups_rider_id_fkey(display_name)," +
            "address:addresses!pickups_address_id_fkey(address_line,latitude,longitude)"

    /** The latest pickup for tracking (any status). */
    suspend fun loadLatestPickup(): PickupDto? {
        val id = uid ?: return null
        return list(
            "pickups?generator_id=eq.$id&select=$PICKUP_SELECT&order=created_at.desc&limit=1",
            PickupDto.serializer(),
        ).firstOrNull()
    }

    /** Recent pickups for the history timeline. */
    suspend fun loadPickups(limit: Int = 20): List<PickupDto> {
        val id = uid ?: return emptyList()
        return list(
            "pickups?generator_id=eq.$id&select=$PICKUP_SELECT&order=created_at.desc&limit=$limit",
            PickupDto.serializer(),
        )
    }

    private const val RIDER_PROFILE_SELECT =
        "rating,vehicle_type,rating_count,total_collections,total_recycled_kg," +
            "co2_avoided_kg,earnings_balance,earnings_lifetime,monthly_goal"

    suspend fun loadRiderProfile(riderId: String): RiderProfileDto? = list(
        "rider_profiles?profile_id=eq.$riderId&select=$RIDER_PROFILE_SELECT",
        RiderProfileDto.serializer(),
    ).firstOrNull()

    /** The signed-in rider's own profile totals (stats + earnings screens). */
    suspend fun loadMyRiderProfile(): RiderProfileDto? = uid?.let { loadRiderProfile(it) }

    // ---- Collection centers (acopio map) ----

    suspend fun loadCenters(): List<CollectionCenterDto> = list(
        "collection_centers?select=id,name,address_line,latitude,longitude,phone,is_open," +
            "center_materials(buy_price_per_kg,materials(code,name))&order=name.asc",
        CollectionCenterDto.serializer(),
    )

    // ---- Detections (history) ----

    suspend fun loadDetections(limit: Int = 20): List<DetectionDto> {
        val id = uid ?: return emptyList()
        return list(
            "detections?generator_id=eq.$id" +
                "&select=status,total_points,total_value,total_weight_kg,detected_at," +
                "detection_items(quantity,confidence,quality_score,points,materials(id,name,code,icon))" +
                "&order=detected_at.desc&limit=$limit",
            DetectionDto.serializer(),
        )
    }

    // ---- AI scan ----

    /**
     * Scores one frame of the live scan. Nothing is saved: the server returns what it
     * saw plus a signature over that count ([DetectResultDto.token]), which
     * [commitScan] replays. [DetectResultDto.rejected] carries the anti-spoof and
     * "nothing here" outcomes — the camera stays open through all of them.
     */
    suspend fun previewFrame(sessionId: String, frame: String, motion: MotionDto): DetectResultDto =
        callDetect(DetectRequest(sessionId, "preview", listOf(frame), motion))

    /**
     * Closes the scan: the server re-checks its own signatures, takes the best count per
     * material across the session and is the only writer of `detections` — so the result
     * is already persisted (status 'pending') unless [DetectResultDto.rejected] is set.
     */
    suspend fun commitScan(sessionId: String, observations: List<ScanObservationDto>): DetectResultDto =
        callDetect(DetectRequest(sessionId, "commit", observations = observations))

    private suspend fun callDetect(request: DetectRequest): DetectResultDto {
        val body = MakiApi.json.encodeToString(DetectRequest.serializer(), request)
        val raw = MakiApi.callFunction("detect-material", body)
        return MakiApi.json.decodeFromString(DetectResultDto.serializer(), raw)
    }

    /** Asks the Maki assistant agent; [turns] is the recent conversation (role/content). */
    suspend fun askAssistant(turns: List<ChatTurnDto>): String {
        val body = MakiApi.json.encodeToString(AssistantRequest.serializer(), AssistantRequest(turns))
        val raw = MakiApi.callFunction("maki-assistant", body)
        return MakiApi.json.decodeFromString(AssistantReplyDto.serializer(), raw).reply
            ?: error("Empty assistant reply")
    }

    // ---- Rider (dashboard + collection flow) ----

    private const val RIDER_PICKUP_SELECT =
        "id,code,status,scheduled_date,window_label,eta_minutes,rider_earnings,total_weight_kg," +
            "generator_points_awarded,rider_id,created_at,completed_at," +
            "match_status,condition,notes,cancel_reason,latitude,longitude,distance_m," +
            "pickup_items(reported_quantity,confirmed_quantity,points,materials(id,name,code,icon))," +
            "generator:profiles!pickups_generator_id_fkey(display_name)," +
            "address:addresses!pickups_address_id_fkey(address_line,reference,latitude,longitude)"

    /** Unassigned requests a rider can take (RLS already scopes visibility). */
    suspend fun loadAvailablePickups(): List<PickupDto> = list(
        "pickups?status=eq.requested&rider_id=is.null&select=$RIDER_PICKUP_SELECT&order=created_at.asc",
        PickupDto.serializer(),
    )

    /** The rider's in-flight pickup, if any. */
    suspend fun loadMyRiderPickup(): PickupDto? {
        val id = uid ?: return null
        return list(
            "pickups?rider_id=eq.$id&status=in.(assigned,en_route,arriving,collecting)" +
                "&select=$RIDER_PICKUP_SELECT&order=created_at.desc&limit=1",
            PickupDto.serializer(),
        ).firstOrNull()
    }

    /**
     * Atomically claims a requested pickup (the status+rider filters make the race safe:
     * zero rows update if another rider won). Returns false when it was already taken.
     */
    suspend fun acceptPickup(pickupId: String): Boolean {
        val id = uid ?: error("No session")
        // The fee is NOT sent from here: `authenticated` no longer has UPDATE on
        // rider_earnings, and the trg_set_rider_fee trigger prices the stop on
        // assignment (base + per-km). A tampered client can no longer pay itself.
        val rows = MakiApi.patch(
            "pickups?id=eq.$pickupId&status=eq.requested&rider_id=is.null",
            """{"rider_id":"$id","status":"assigned"}""",
            prefer = "return=representation",
        )
        return rows.trim() != "[]"
    }

    /** Advances the rider's own pickup along the route (en_route / collecting …). */
    suspend fun setPickupStatus(pickupId: String, status: String) {
        MakiApi.patch("pickups?id=eq.$pickupId", """{"status":"$status"}""")
    }

    /** The rider's settled earnings, newest first (drives the "hoy" stat). */
    suspend fun loadRiderEarnings(limit: Int = 50): List<RiderEarningDto> {
        val id = uid ?: return emptyList()
        return list(
            "rider_earnings?rider_id=eq.$id&select=amount,created_at,type,description" +
                "&order=created_at.desc&limit=$limit",
            RiderEarningDto.serializer(),
        )
    }

    /** Display name of the signed-in profile (any role). */
    suspend fun loadMyDisplayName(): String? {
        val id = uid ?: return null
        return list("profiles?id=eq.$id&select=display_name", RiderNameDto.serializer())
            .firstOrNull()?.display_name
    }

    // ---- Challenges (retos) ----

    /** Active challenges with the caller's progress (RLS scopes the embed to own rows). */
    suspend fun loadChallenges(): List<ChallengeDto> = list(
        "challenges?is_active=eq.true" +
            "&select=id,name,description,target_value,target_unit,reward_points," +
            "user_challenges(progress,is_completed)&order=created_at.asc",
        ChallengeDto.serializer(),
    )

    // ---- Chat history (persisted; RLS restricts rows to their owner) ----

    /** The user's conversations, most recently active first. */
    suspend fun loadConversations(): List<ChatConversationDto> {
        val id = uid ?: return emptyList()
        return list(
            "chat_conversations?user_id=eq.$id&select=id,title,last_message_at,created_at" +
                "&order=last_message_at.desc.nullslast&limit=30",
            ChatConversationDto.serializer(),
        )
    }

    suspend fun loadChatMessages(conversationId: String): List<ChatMessageDto> = list(
        "chat_messages?conversation_id=eq.$conversationId&role=in.(user,assistant)" +
            "&select=role,content&order=id.asc",
        ChatMessageDto.serializer(),
    )

    /** Creates a conversation titled after its first question; returns the new id. */
    suspend fun createConversation(title: String): String {
        val id = uid ?: error("No session")
        val body = MakiApi.json.encodeToString(NewConversation.serializer(), NewConversation(user_id = id, title = title))
        return MakiApi.json
            .decodeFromString(ListSerializer(ChatConversationDto.serializer()), MakiApi.post("chat_conversations?select=id", body))
            .firstOrNull()?.id ?: error("Conversation not created")
    }

    /** Appends a turn; the trg_chat_bump DB trigger refreshes the conversation's recency. */
    suspend fun saveChatMessage(conversationId: String, role: String, content: String) {
        val body = MakiApi.json.encodeToString(
            NewChatMessage.serializer(),
            NewChatMessage(conversation_id = conversationId, role = role, content = content),
        )
        MakiApi.post("chat_messages", body, prefer = "return=minimal")
    }

    /** Live market prices per material (public view; works without a session). */
    suspend fun loadPrices(): List<MaterialPriceDto> =
        list("v_material_prices?select=code,name,price_per_kg,pct_change", MaterialPriceDto.serializer())

    // ---- Writes ----

    /** Spends points on a redemption. DB triggers validate the balance and post the ledger. */
    suspend fun createRedemption(optionId: String?, kind: String, pointsSpent: Int, monetaryValue: Double) {
        val id = uid ?: error("No session")
        val body = MakiApi.json.encodeToString(
            NewRedemption.serializer(),
            NewRedemption(user_id = id, option_id = optionId, kind = kind, points_spent = pointsSpent, monetary_value = monetaryValue),
        )
        MakiApi.post("redemptions", body, prefer = "return=minimal")
    }

    /** Creates a 'requested' pickup with its items; returns the new pickup id. */
    suspend fun createPickup(
        addressId: String?,
        scheduledDate: String?,
        windowLabel: String?,
        items: List<Pair<String, Int>>, // materialId to reported_quantity
    ): String {
        val id = uid ?: error("No session")
        val pickupBody = MakiApi.json.encodeToString(
            NewPickup.serializer(),
            NewPickup(generator_id = id, address_id = addressId, scheduled_date = scheduledDate, window_label = windowLabel),
        )
        val pickupId = MakiApi.json
            .decodeFromString(ListSerializer(PickupIdDto.serializer()), MakiApi.post("pickups?select=id", pickupBody))
            .firstOrNull()?.id ?: error("Pickup not created")

        val rows = items.filter { it.second > 0 }
            .map { NewPickupItem(pickup_id = pickupId, material_id = it.first, reported_quantity = it.second) }
        if (rows.isNotEmpty()) {
            val itemsBody = MakiApi.json.encodeToString(ListSerializer(NewPickupItem.serializer()), rows)
            MakiApi.post("pickup_items", itemsBody, prefer = "return=minimal")
        }
        return pickupId
    }

    /**
     * Creates a pickup from the caller's latest pending detection (links it, copies its
     * items, sets points server-side, moves the detection to 'scheduled'). Returns id.
     */
    suspend fun createPickupFromDetection(
        addressId: String?,
        scheduledDate: String?,
        windowLabel: String?,
    ): String? {
        val body = MakiApi.json.encodeToString(
            RpcCreatePickup.serializer(),
            RpcCreatePickup(p_address_id = addressId, p_scheduled_date = scheduledDate, p_window_label = windowLabel),
        )
        // The RPC returns a bare uuid; PostgREST renders a scalar as a quoted string.
        return MakiApi.post("rpc/create_pickup_from_detection", body)
            .trim().trim('"').takeIf { it.isNotBlank() && it != "null" }
    }

    /**
     * Re-states what the family says they actually have, per material. Only the
     * *reported* count — the rider still writes `confirmed_quantity`, which is what
     * the points settle against, so correcting this never pays anybody.
     */
    suspend fun updateReportedQuantities(pickupId: String, items: List<Pair<String, Int>>) {
        items.forEach { (materialId, qty) ->
            MakiApi.patch(
                "pickup_items?pickup_id=eq.$pickupId&material_id=eq.$materialId",
                """{"reported_quantity":$qty}""",
            )
        }
    }

    /**
     * Hands the pickup to the closest available Eco-Rider (the taxi-app dispatch).
     * Returns null when nobody is available — the request then stays on the board
     * for a rider to claim, exactly as before.
     */
    suspend fun assignNearestRider(pickupId: String): AssignedRiderDto? {
        val body = MakiApi.json.encodeToString(RpcAssignRider.serializer(), RpcAssignRider(pickupId))
        val text = MakiApi.post("rpc/assign_nearest_rider", body)
        return MakiApi.json
            .decodeFromString(ListSerializer(AssignedRiderDto.serializer()), text)
            .firstOrNull()
            ?.takeIf { it.rider_id != null }
    }

    /** Live position of the rider working the caller's pickup (null until they report one). */
    suspend fun loadRiderPosition(riderId: String): RiderPositionDto? = list(
        "rider_profiles?profile_id=eq.$riderId&select=current_latitude,current_longitude,last_location_at",
        RiderPositionDto.serializer(),
    ).firstOrNull()

    /** Publishes the rider's own position so the family can watch them approach. */
    suspend fun publishRiderLocation(lat: Double, lng: Double) {
        val id = uid ?: return
        val body = MakiApi.jsonFull.encodeToString(
            RiderLocationPatch.serializer(),
            RiderLocationPatch(lat, lng, isoNow()),
        )
        MakiApi.patch("rider_profiles?profile_id=eq.$id", body)
    }

    /** UTC timestamp PostgREST accepts (no java.time — minSdk 24). */
    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

    /** Marks a pickup completed → settle_pickup credits the promised points. */
    suspend fun completePickup(pickupId: String) {
        val body = MakiApi.json.encodeToString(RpcCompletePickup.serializer(), RpcCompletePickup(pickupId))
        MakiApi.post("rpc/complete_pickup", body, prefer = "return=minimal")
    }

    suspend fun markAllNotificationsRead() {
        val id = uid ?: return
        MakiApi.patch("notifications?user_id=eq.$id&is_read=eq.false", """{"is_read":true}""")
    }

    /** Settled/closed stops for the rider history timeline. */
    suspend fun loadRiderHistory(limit: Int = 40): List<PickupDto> {
        val id = uid ?: return emptyList()
        return list(
            "pickups?rider_id=eq.$id&status=in.(completed,cancelled,failed,no_match)" +
                "&select=$RIDER_PICKUP_SELECT&order=created_at.desc&limit=$limit",
            PickupDto.serializer(),
        )
    }

    /**
     * The rider's optimised route for [date] (today by default), stops in sequence.
     * Returns null when the operator has not planned one — the caller then falls
     * back to the rider's own assigned pickups.
     */
    suspend fun loadTodayRoute(date: String): RouteDto? {
        val id = uid ?: return null
        return list(
            "routes?rider_id=eq.$id&route_date=eq.$date" +
                "&select=id,status,route_date,total_stops,total_distance_m,total_duration_min," +
                "route_stops(sequence,status,eta_minutes,distance_from_prev_m," +
                "pickups(id,code,status,window_label,eta_minutes,latitude,longitude,distance_m," +
                "pickup_items(reported_quantity,confirmed_quantity,points,materials(id,name,code,icon))," +
                "generator:profiles!pickups_generator_id_fkey(display_name)," +
                "address:addresses!pickups_address_id_fkey(address_line,reference,latitude,longitude)))" +
                "&order=created_at.desc&limit=1",
            RouteDto.serializer(),
        ).firstOrNull()
    }

    /** Every stop currently assigned to the rider — the route fallback + map pins. */
    suspend fun loadMyAssignedPickups(): List<PickupDto> {
        val id = uid ?: return emptyList()
        return list(
            "pickups?rider_id=eq.$id&status=in.(assigned,en_route,arriving,collecting)" +
                "&select=$RIDER_PICKUP_SELECT&order=created_at.asc",
            PickupDto.serializer(),
        )
    }

    /** Badges: unlocked first, for the stats screen. */
    suspend fun loadMyAchievements(): List<UserAchievementDto> {
        val id = uid ?: return emptyList()
        return list(
            "user_achievements?user_id=eq.$id&select=progress,unlocked,achievements(name,icon,tier)" +
                "&order=unlocked.desc,unlocked_at.desc.nullslast&limit=12",
            UserAchievementDto.serializer(),
        )
    }

    suspend fun markNotificationRead(notificationId: Long) {
        val id = uid ?: return
        MakiApi.patch("notifications?id=eq.$notificationId&user_id=eq.$id", """{"is_read":true}""")
    }
}

@kotlinx.serialization.Serializable
private data class PickupIdDto(val id: String)
