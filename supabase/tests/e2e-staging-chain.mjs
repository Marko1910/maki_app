#!/usr/bin/env node
/**
 * Una cadena HTTP real, Node 24, sin SDK ni dependencias.
 *   node supabase/tests/e2e-staging-chain.mjs --dry-run
 *   node supabase/tests/e2e-staging-chain.mjs
 *
 * Fuentes: pwa/src/lib/{api,config}.ts, dashboard/lib/queries.ts,
 * dashboard/app/(panel)/dashboard/page.tsx, CLAUDE.md y los SQL
 * 2026-10-19_cadena_entregas / 2026-11-03_vision_measurement.
 * Conserva el recojo, entrega, libros y avisos para inspección; no hace limpieza
 * contable. Usar las tres cuentas de seed sin actividad concurrente. Si despacho
 * elige otro rider, aborta: no cambia su sesión ni fuerza una reasignación.
 * El peso de prueba es 3 * avg_weight_kg (simulación, no medición física),
 * grado B / factor 0.85 como el formulario, precio vigente del centro.
 * No escribe puntos, importes liquidados, detecciones ni analítica privada.
 * La captura privada de visión no se puede leer con estas cuentas; una petición
 * manual tampoco produce una observación IA que permita probar esa medición.
 * Los SQL locales no definen los triggers de notificación de entregas:
 * se informa lo observado, sin inventar destinatarios ni tipos obligatorios.
 */
import { readFile } from 'node:fs/promises';

const STAGING = 'https://mbbdrmicjobydprvbpsj.supabase.co';
const PROJECT = 'mbbdrmicjobydprvbpsj';
const PASSWORD = 'Maki12345!'; // Seed público documentado en CLAUDE.md.
const ACCOUNTS = {
  generator: ['generador@ruma.test', 'generador'],
  rider: ['rider@ruma.test', 'eco_rider'],
  operator: ['operador@ruma.test', 'operador'],
};
const dryRun = process.argv.includes('--dry-run');
const secrets = new Set([PASSWORD]);
const results = [];
const state = { pickup: null, delivery: null };
let config;
let requests = 0;

function safe(value) {
  let text = typeof value === 'string' ? value : JSON.stringify(value);
  for (const secret of secrets) text = text.replaceAll(secret, '[oculto]');
  return text.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[token oculto]')
    .replace(/[\r\n\t|]/g, ' ');
}

function report(step, status, observed) {
  const row = { paso: step, resultado: status, observado: safe(observed) };
  results.push(row);
  console.log(`| ${row.paso} | ${status} | ${row.observado} |`);
  if (status === 'FALLA') process.exitCode = 1;
}

function requireThat(condition, message) {
  if (!condition) throw new Error(message);
}

function check(step, condition, observed) {
  report(step, condition ? 'PASA' : 'FALLA', observed);
}

function one(rows, label) {
  const list = Array.isArray(rows) ? rows : rows ? [rows] : [];
  requireThat(list.length === 1, `${label}: esperaba una fila, observadas ${list.length}`);
  return list[0];
}

function number(value) {
  requireThat(value !== null && value !== undefined && Number.isFinite(Number(value)),
    `Dato numérico ausente o inválido: ${String(value)}`);
  return Number(value);
}

const close = (a, b) => Math.abs(number(a) - number(b)) < 0.00001;
const sum = (rows) => rows.reduce((total, row) => total + number(row.amount), 0);
const newRows = (before, after) => {
  requireThat([...before, ...after].every((row) => row.id != null), 'Falta id para comparar filas nuevas');
  const old = new Set(before.map((row) => String(row.id)));
  return after.filter((row) => !old.has(String(row.id)));
};

async function readConfig() {
  requireThat(Number(process.versions.node.split('.')[0]) >= 24, 'Se requiere Node 24 o posterior');
  requireThat(process.argv.slice(2).every((arg) => arg === '--dry-run'), 'Único argumento admitido: --dry-run');
  const source = await readFile(new URL('../../pwa/src/lib/config.ts', import.meta.url), 'utf8');
  const url = source.match(/const\s+STAGING_URL\s*=\s*["']([^"']+)["']/)?.[1];
  const key = source.match(/const\s+STAGING_ANON_KEY\s*=\s*["']([^"']+)["']/)?.[1];
  requireThat(url === STAGING, 'ABORTADO: STAGING_URL de config.ts no es el staging autorizado');
  // Fail closed si cambia la forma en que config.ts selecciona el proyecto.
  requireThat(/export const SUPABASE_URL:\s*string\s*=\s*import\.meta\.env\.VITE_SUPABASE_URL\s*\?\?\s*STAGING_URL\s*;/.test(source),
    'ABORTADO: selección de URL desconocida en config.ts; revisar antes de ejecutar');
  requireThat(/export const SUPABASE_ANON_KEY:\s*string\s*=\s*import\.meta\.env\.VITE_SUPABASE_ANON_KEY\s*\?\?\s*STAGING_ANON_KEY\s*;/.test(source),
    'ABORTADO: selección de clave pública desconocida en config.ts');
  if (process.env.VITE_SUPABASE_URL !== undefined) {
    requireThat(process.env.VITE_SUPABASE_URL === STAGING, 'ABORTADO: VITE_SUPABASE_URL no es staging');
  }
  // No carga .env ni claves de entorno: solo la anon pública de config.ts.
  requireThat(Boolean(key), 'Falta STAGING_ANON_KEY pública');
  secrets.add(key);
  let claims;
  try { claims = JSON.parse(Buffer.from(key.split('.')[1], 'base64url').toString()); }
  catch { throw new Error('Formato de clave pública inesperado'); }
  requireThat(claims.role === 'anon' && claims.ref === PROJECT, 'La clave no es anon del staging autorizado');
  return { url, key };
}

class HttpError extends Error {
  constructor(status, data) {
    super(`HTTP ${status} ${safe(data?.code ?? '')}: ${safe(data?.message ?? data?.error_description ?? 'sin detalle')}`);
    this.status = status;
    this.code = data?.code;
    this.detail = String(data?.message ?? '');
  }
}

async function request(actor, path, { method = 'GET', body, query = {} } = {}) {
  requireThat(!dryRun, 'BUG: --dry-run intentó hacer una petición');
  const url = new URL(path, config.url);
  requireThat(url.origin === STAGING && ['/auth/v1/token', '/rest/v1/'].some((p) => url.pathname.startsWith(p)),
    'Destino HTTP no autorizado');
  if (actor) requireThat(Object.values(ACCOUNTS).some(([email]) => email === actor.email), 'Cuenta no autorizada');
  for (const [key, value] of Object.entries(query)) url.searchParams.set(key, String(value));
  requests += 1;
  const response = await fetch(url, {
    method, redirect: 'error', signal: AbortSignal.timeout(20_000),
    headers: {
      apikey: config.key,
      Authorization: `Bearer ${actor?.token ?? config.key}`,
      'Content-Type': 'application/json',
      ...(method === 'POST' || method === 'PATCH' ? { Prefer: 'return=representation' } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : null; }
  catch { throw new Error(`HTTP ${response.status}: respuesta no JSON (cuerpo no impreso)`); }
  if (!response.ok) throw new HttpError(response.status, data);
  return data;
}

const get = (actor, table, query) => request(actor, `/rest/v1/${table}`, { query });
const insert = (actor, table, body) => request(actor, `/rest/v1/${table}`, { method: 'POST', body });
const patch = (actor, table, body, query) => request(actor, `/rest/v1/${table}`, { method: 'PATCH', body, query });
const rpc = (actor, name, body) => request(actor, `/rest/v1/rpc/${name}`, { method: 'POST', body });

// Paginar evita perder una notificación/libro por el límite de PostgREST.
async function all(actor, table, query = {}) {
  const rows = [];
  for (let offset = 0; ; offset += 100) {
    const page = await get(actor, table, { select: '*', order: 'id.asc', ...query, limit: 100, offset });
    requireThat(Array.isArray(page), `${table}: respuesta no es una lista`);
    rows.push(...page);
    if (page.length < 100) return rows;
  }
}

async function login(key) {
  const [email, role] = ACCOUNTS[key];
  requireThat(email.endsWith('@ruma.test'), 'Solo se admiten cuentas @ruma.test');
  const session = await request(null, '/auth/v1/token', {
    method: 'POST', query: { grant_type: 'password' }, body: { email, password: PASSWORD },
  });
  for (const value of [session?.access_token, session?.refresh_token]) if (value) secrets.add(value);
  requireThat(session?.user?.email?.toLowerCase() === email && session?.access_token, `Identidad inesperada para ${email}`);
  const actor = { id: session.user.id, email, token: session.access_token };
  const profile = one(await get(actor, 'profiles', { select: 'id,role', id: `eq.${actor.id}` }), 'Perfil');
  requireThat(profile.role === role, `${email}: rol observado ${profile.role}, esperado ${role}`);
  report(`Sesión ${role}`, 'PASA', email);
  return actor;
}

async function pickup(actor = state.generator) {
  return one(await get(actor, 'pickups', { select: '*', id: `eq.${state.pickup.id}` }), 'Recojo');
}

async function delivery(actor = state.operator, id = state.delivery.id) {
  return one(await get(actor, 'deliveries', { select: '*', id: `eq.${id}` }), 'Entrega');
}

async function accounting() {
  const generator = one(await get(state.generator, 'generator_profiles', {
    select: 'points_balance,points_pending', profile_id: `eq.${state.generator.id}`,
  }), 'Saldo recolector');
  const rider = one(await get(state.rider, 'rider_profiles', {
    select: 'earnings_balance,earnings_lifetime', profile_id: `eq.${state.rider.id}`,
  }), 'Saldo rider');
  const earnings = await all(state.rider, 'rider_earnings', { rider_id: `eq.${state.rider.id}` });
  const points = await all(state.generator, 'point_transactions', { user_id: `eq.${state.generator.id}` });
  return { generator, rider, earnings, points };
}

function accountingSummary(value) {
  return { ...value.generator, ...value.rider, rider_earnings_filas: value.earnings.length,
    rider_earnings_total: sum(value.earnings) };
}

function unchanged(before, after) {
  return ['points_pending', 'points_balance'].every((key) => close(before.generator[key], after.generator[key]))
    && ['earnings_balance', 'earnings_lifetime'].every((key) => close(before.rider[key], after.rider[key]))
    && newRows(before.earnings, after.earnings).length === 0
    && newRows(before.points, after.points).length === 0;
}

// Correlación por ID/referencia; admite avisos antiguos que solo incluyen el código
// en el texto. Nunca basta que haya aumentado el total global de notificaciones.
function relates(row, records) {
  const refs = [row.reference_id, row.pickup_id, row.delivery_id, row.data?.pickup_id, row.data?.delivery_id];
  const text = `${row.title ?? ''} ${row.body ?? ''} ${row.description ?? ''}`;
  return records.some((record) => record && (refs.includes(record.id)
    || (record.code && text.includes(record.code)) || text.includes(record.id)));
}

async function notificationSnapshot(actors) {
  const snapshots = {};
  for (const actor of actors) {
    snapshots[actor.email] = await all(actor, 'notifications', { user_id: `eq.${actor.id}` });
  }
  return snapshots;
}

async function notificationsAfter(label, before, actors, records, required) {
  const after = await notificationSnapshot(actors);
  for (const actor of actors) {
    const fresh = newRows(before[actor.email], after[actor.email]).filter((row) => relates(row, records));
    check(`Avisos ${label} / ${actor.email}`, !required || fresh.length > 0, {
      nuevos_vinculados: fresh.length,
      avisos: fresh.map(({ id, kind, title, body, reference_type, reference_id }) =>
        ({ id, kind, title, body, reference_type, reference_id })),
      ...(!required ? { alcance: 'Lectura verificada; destinatarios obligatorios no definidos en SQL local' } : {}),
    });
  }
}

async function pickupTransition(status, action) {
  const actors = [state.generator, state.rider];
  const before = await notificationSnapshot(actors);
  const observed = await action();
  const row = await pickup();
  requireThat(row.status === status && row.rider_id === state.rider.id,
    `Estado/rider inesperado: ${row.status}, rider=${row.rider_id}`);
  report(`Estado ${status}`, 'PASA', { estado: row.status, rider: state.rider.email, resultado: observed ?? null });
  await notificationsAfter(status, before, actors, [state.pickup], true);
}

async function deliveryTransition(status, action) {
  const actors = [state.generator, state.rider, state.operator];
  const before = await notificationSnapshot(actors);
  await action();
  const row = await delivery();
  requireThat(row.status === status && row.center_id === state.center.id,
    `Entrega inesperada: estado=${row.status}, centro=${row.center_id}`);
  report(`Entrega ${status}`, 'PASA', { codigo: row.code, estado: row.status, centro: row.center_id });
  await notificationsAfter(status, before, actors, [state.pickup, state.delivery], false);
  return row;
}

async function denied(label, action) {
  let rejection;
  try { await action(); }
  catch (error) {
    // Un timeout, JWT expirado, RPC ausente o fallo SQL NO prueban autorización.
    if (!(error instanceof HttpError) || error.status === 401
      || !((error.code === '42501') || (error.code === 'P0001'
        && /not permitted|not allowed|forbidden|permission denied|only.*rider|solo.*rider|no.*autoriz|no.*permit|rider.*asignado|rider.*assigned/i.test(error.detail)))) {
      throw new Error(`${label}: no se acreditó rechazo por permisos: ${safe(error.message)}`);
    }
    rejection = error;
  }
  requireThat(rejection, `${label}: la operación fue aceptada indebidamente; se detiene la cadena`);
  report(label, 'PASA', rejection.message);
}

const steps = [
  ['Autenticar las tres cuentas de staging', async () => {
    state.generator = await login('generator');
    state.rider = await login('rider');
    state.operator = await login('operator');
  }],
  ['Resolver centro, PET y dirección del seed', async () => {
    // miCentro(): operator_profiles visible por RLS, luego collection_centers.
    const op = one(await get(state.operator, 'operator_profiles', { select: 'center_id' }), 'Centro del operador');
    requireThat(op.center_id, 'El operador no tiene centro');
    state.center = one(await get(state.operator, 'collection_centers', { select: 'id,name', id: `eq.${op.center_id}` }), 'Centro');
    state.material = one(await get(state.generator, 'materials', {
      select: 'id,code,points_per_unit,points_per_kg,avg_weight_kg', code: 'eq.PET', is_active: 'eq.true',
    }), 'PET activo');
    state.price = one(await get(state.operator, 'center_materials', {
      select: 'material_id,buy_price_per_kg,is_accepted', center_id: `eq.${state.center.id}`, material_id: `eq.${state.material.id}`,
    }), 'PET del centro');
    requireThat(state.price.is_accepted && number(state.price.buy_price_per_kg) > 0, 'El centro debe aceptar PET con precio positivo');
    state.weight = Number((3 * number(state.material.avg_weight_kg)).toFixed(3));
    requireThat(state.weight > 0 && Math.floor(state.weight * number(state.material.points_per_kg)) > 0,
      'El peso simulado de 3 PET debe generar puntos positivos');
    const addresses = await get(state.generator, 'addresses', {
      select: 'id,latitude,longitude', profile_id: `eq.${state.generator.id}`, order: 'is_primary.desc', limit: 1,
    });
    state.address = addresses[0] ?? null;
    state.initial = await accounting();
    return { centro: state.center, pet: state.material.id, peso_simulado_kg: state.weight,
      grado: 'B', precio_kg: state.price.buy_price_per_kg, inicial: accountingSummary(state.initial) };
  }],
  ['Crear recojo manual con 2 PET', async () => {
    const localDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
    const address = state.address;
    // createPickup: primero pickups, después pickup_items; nunca detection_id ni puntos.
    state.pickup = one(await insert(state.generator, 'pickups', {
      generator_id: state.generator.id, address_id: address?.id ?? null,
      scheduled_date: localDate, window_label: 'E2E staging',
      ...(address?.latitude != null && address?.longitude != null
        ? { latitude: address.latitude, longitude: address.longitude } : {}),
    }), 'Recojo creado');
    one(await insert(state.generator, 'pickup_items', [{ pickup_id: state.pickup.id,
      material_id: state.material.id, reported_quantity: 2 }]), 'PET declarado');
    const row = await pickup();
    state.pickup = row;
    requireThat(row.status === 'requested' && row.detection_id == null && row.generator_id === state.generator.id,
      `Recojo manual inesperado: ${row.status}`);
    const item = one(await get(state.generator, 'pickup_items', { pickup_id: `eq.${row.id}` }), 'Declaración');
    requireThat(number(item.reported_quantity) === 2, 'No se guardaron las 2 unidades PET');
    return { codigo: row.code, id: row.id, estado: row.status, PET: item.reported_quantity };
  }],
  ['Asignar rider por despacho o tablero', async () => {
    await pickupTransition('assigned', async () => {
      const result = await rpc(state.generator, 'assign_nearest_rider', { p_pickup_id: state.pickup.id });
      requireThat(result == null || Array.isArray(result), 'assign_nearest_rider devolvió un formato distinto al usado por api.ts');
      const assigned = result?.[0]?.rider_id;
      if (assigned) {
        requireThat(assigned === state.rider.id,
          'El despacho eligió otro rider: se detiene sin iniciar sesión ni operar como esa cuenta. Requiere seed aislado.');
        return 'assign_nearest_rider';
      }
      const board = await get(state.rider, 'pickups', {
        select: 'id', id: `eq.${state.pickup.id}`, status: 'eq.requested', rider_id: 'is.null',
      });
      one(board, 'Recojo visible en tablero');
      one(await patch(state.rider, 'pickups', { rider_id: state.rider.id, status: 'assigned' }, {
        id: `eq.${state.pickup.id}`, status: 'eq.requested', rider_id: 'is.null',
      }), 'acceptPickup (cero filas significa carrera perdida)');
      return 'assign_nearest_rider sin rider; acceptPickup desde tablero';
    });
  }],
  ['Avanzar a en_route', () => pickupTransition('en_route', async () => {
    one(await patch(state.rider, 'pickups', { status: 'en_route' }, { id: `eq.${state.pickup.id}` }), 'en_route');
  })],
  ['Publicar ubicación y leer seguimiento como recolector', async () => {
    const rider = one(await get(state.rider, 'rider_profiles', {
      select: 'current_latitude,current_longitude', profile_id: `eq.${state.rider.id}`,
    }), 'Ubicación previa');
    const latitude = state.address?.latitude ?? rider.current_latitude;
    const longitude = state.address?.longitude ?? rider.current_longitude;
    requireThat(latitude != null && longitude != null, 'El seed no tiene coordenadas para simular ubicación');
    const fix = { current_latitude: number(latitude), current_longitude: number(longitude), last_location_at: new Date().toISOString() };
    one(await patch(state.rider, 'rider_profiles', fix, { profile_id: `eq.${state.rider.id}` }), 'publishRiderLocation');
    const tracked = await pickup();
    requireThat(tracked.rider_id === state.rider.id && tracked.generator_id === state.generator.id, 'Seguimiento de otro actor');
    const seen = one(await get(state.generator, 'rider_profiles', {
      select: 'current_latitude,current_longitude,last_location_at', profile_id: `eq.${tracked.rider_id}`,
    }), 'loadRiderPosition');
    requireThat(close(seen.current_latitude, fix.current_latitude) && close(seen.current_longitude, fix.current_longitude)
      && Date.parse(seen.last_location_at) === Date.parse(fix.last_location_at), `Ubicación no coincide: ${JSON.stringify(seen)}`);
    return { ubicacion_simulada_leida: seen };
  }],
  ...['arriving', 'collecting'].map((status) => [`Avanzar a ${status}`, () => pickupTransition(status, async () => {
    one(await patch(state.rider, 'pickups', { status }, { id: `eq.${state.pickup.id}` }), status);
  })]),
  ['Confirmar 3 PET sin pagar desde el cliente', async () => {
    const item = one(await patch(state.rider, 'pickup_items', { confirmed_quantity: 3 }, {
      pickup_id: `eq.${state.pickup.id}`, material_id: `eq.${state.material.id}`,
    }), 'confirmQuantities');
    requireThat(number(item.confirmed_quantity) === 3 && number(item.reported_quantity) === 2, 'Declaración/confirmación inesperadas');
    state.beforeComplete = await accounting();
    requireThat(unchanged(state.initial, state.beforeComplete), 'Crear/asignar/confirmar cambió saldos o libros antes de complete_pickup');
    return { declaradas: item.reported_quantity, confirmadas: item.confirmed_quantity, antes: accountingSummary(state.beforeComplete) };
  }],
  ['Negativo: recolector no puede completar', async () => {
    await denied('complete_pickup como generador', () => rpc(state.generator, 'complete_pickup', { p_pickup_id: state.pickup.id }));
    const row = await pickup();
    requireThat(row.status === 'collecting' && unchanged(state.beforeComplete, await accounting()),
      'El intento del recolector alteró el estado o los saldos');
    return { estado: row.status, saldos_sin_cambios: true };
  }],
  ['Completar como rider y verificar pendientes/ganancia', async () => {
    await pickupTransition('completed', async () => {
      await rpc(state.rider, 'complete_pickup', { p_pickup_id: state.pickup.id });
    });
    state.completed = await pickup();
    requireThat(Boolean(state.completed.completed_at), 'Recojo completed sin completed_at');
    state.afterComplete = await accounting();
    const before = state.beforeComplete;
    const after = state.afterComplete;
    const awarded = number(state.completed.generator_points_awarded);
    const fee = number(state.completed.rider_earnings);
    const fresh = newRows(before.earnings, after.earnings);
    check('Pendientes tras complete_pickup', awarded > 0
      && close(number(after.generator.points_pending) - number(before.generator.points_pending), awarded)
      && close(before.generator.points_balance, after.generator.points_balance)
      && newRows(before.points, after.points).length === 0,
    { antes: before.generator, despues: after.generator, generator_points_awarded: awarded });
    check('Libro rider_earnings y saldo', fee > 0 && fresh.length > 0
      && fresh.every((row) => relates(row, [state.pickup])) && close(sum(fresh), fee)
      && close(number(after.rider.earnings_balance) - number(before.rider.earnings_balance), fee)
      && close(number(after.rider.earnings_lifetime) - number(before.rider.earnings_lifetime), fee),
    { antes: accountingSummary(before), despues: accountingSummary(after), tarifa_recojo: fee,
      nuevas: fresh.map(({ id, amount, type, pickup_id, reference_id, description }) => ({ id, amount, type, pickup_id, reference_id, description })) });
    const item = one(await get(state.rider, 'pickup_items', { pickup_id: `eq.${state.pickup.id}` }), 'Conteo antes del acopio');
    requireThat(number(item.confirmed_quantity) === 3, 'Se perdió el conteo del rider antes del pesaje');
  }],
  ['Abrir entrega con create_delivery al centro del operador', async () => {
    await deliveryTransition('en_camino', async () => {
      state.delivery = one(await rpc(state.rider, 'create_delivery', {
        p_center_id: state.center.id, p_pickup_ids: [state.pickup.id],
      }), 'create_delivery');
    });
    const links = await get(state.rider, 'delivery_pickups', { select: 'pickup_id', delivery_id: `eq.${state.delivery.id}` });
    requireThat(links.length === 1 && links[0].pickup_id === state.pickup.id, 'Vínculo entrega/recojo incorrecto');
    requireThat(state.delivery.rider_id === state.rider.id, 'Entrega asignada a otro rider');
    return { codigo: state.delivery.code, recojo: state.pickup.code, centro: state.center.id };
  }],
  ['Operador recibe exactamente como el panel', async () => {
    await deliveryTransition('recibida', async () => {
      const row = one(await patch(state.operator, 'deliveries', {
        status: 'recibida', received_at: new Date().toISOString(), received_by: state.operator.id,
      }, { id: `eq.${state.delivery.id}` }), 'recibirEntrega');
      requireThat(row.received_by === state.operator.id && row.received_at, 'Falta receptor/fecha de recepción');
    });
  }],
  ['Operador pesa exactamente como el panel', async () => {
    await deliveryTransition('pesada', async () => {
      one(await insert(state.operator, 'delivery_items', [{ delivery_id: state.delivery.id,
        material_id: state.material.id, weight_kg: state.weight, quality_grade: 'B',
        price_per_kg: number(state.price.buy_price_per_kg), grade_factor: 0.85,
      }]), 'registrarPesaje / delivery_items');
      one(await patch(state.operator, 'deliveries', {
        status: 'pesada', weighed_at: new Date().toISOString(),
      }, { id: `eq.${state.delivery.id}` }), 'registrarPesaje / deliveries');
    });
    const item = one(await get(state.operator, 'delivery_items', { delivery_id: `eq.${state.delivery.id}` }), 'Pesaje persistido');
    requireThat(close(item.weight_kg, state.weight) && item.quality_grade === 'B'
      && close(item.price_per_kg, state.price.buy_price_per_kg) && close(item.grade_factor, 0.85)
      && number(item.subtotal) > 0, 'Pesaje/precio/grado/subtotal persistidos inválidos');
    state.weighed = item;
    state.beforeSettlement = await accounting();
    requireThat(unchanged(state.afterComplete, state.beforeSettlement), 'Recibir/pesar pagó antes de settle_delivery');
    return { kg: item.weight_kg, grado: item.quality_grade, precio: item.price_per_kg, subtotal_servidor: item.subtotal };
  }],
  ['Negativo: rider no puede liquidar una entrega pesada', async () => {
    await denied('settle_delivery como rider', () => rpc(state.rider, 'settle_delivery', { p_delivery_id: state.delivery.id }));
    requireThat((await delivery()).status === 'pesada' && unchanged(state.beforeSettlement, await accounting()),
      'El intento del rider alteró entrega o saldos');
    return { estado: 'pesada', saldos_sin_cambios: true };
  }],
  ['Negativo: operador de otro centro (si hay datos seguros)', async () => {
    // Solo entregas YA liquidadas del rider de prueba: incluso ante una regresión
    // de permisos, la RPC es idempotente y no liquida cargas preexistentes ajenas.
    const candidates = await all(state.rider, 'deliveries', {
      rider_id: `eq.${state.rider.id}`, center_id: `neq.${state.center.id}`, status: 'eq.liquidada',
    });
    let candidate;
    for (const row of candidates) {
      const links = await get(state.rider, 'delivery_pickups', { select: 'pickup_id', delivery_id: `eq.${row.id}` });
      if (!links.length) continue;
      let onlyTestGenerator = true;
      for (const link of links) {
        const pickups = await get(state.rider, 'pickups', { select: 'generator_id', id: `eq.${link.pickup_id}` });
        if (pickups.length !== 1 || pickups[0].generator_id !== state.generator.id) onlyTestGenerator = false;
      }
      if (onlyTestGenerator) { candidate = row; break; }
    }
    if (!candidate) {
      report('Operador / entrega de otro centro', 'OMITIDO',
        'No hay entrega liquidada visible de otro centro con rider y recolector de estas cuentas de prueba. No se fabrican datos.');
      return;
    }
    await denied('settle_delivery de otro centro', () => rpc(state.operator, 'settle_delivery', { p_delivery_id: candidate.id }));
    const after = await delivery(state.rider, candidate.id);
    requireThat(after.status === candidate.status && after.settled_at === candidate.settled_at
      && close(after.generator_points, candidate.generator_points), 'El negativo alteró la entrega ajena');
    return { entrega_existente: candidate.code, centro: candidate.center_id, estado: after.status };
  }],
  ['Operador liquida: pendientes a saldo, entrega y avisos', async () => {
    const settled = await deliveryTransition('liquidada', async () => {
      await rpc(state.operator, 'settle_delivery', { p_delivery_id: state.delivery.id });
    });
    const finalPickup = await pickup();
    const after = await accounting();
    const before = state.beforeSettlement;
    // Un solo recojo/material: la proporción de reported_quantity es 2/2.
    const expected = Math.floor(number(state.weighed.weight_kg) * number(state.material.points_per_kg));
    const awarded = number(state.completed.generator_points_awarded);
    const points = newRows(before.points, after.points);
    const pickupPoints = points.filter((row) => row.type === 'earn_pickup'
      && row.reference_type === 'pickup' && row.reference_id === state.pickup.id);
    check('Puntos definitivos del pesaje', expected > 0 && pickupPoints.length === 1 && close(sum(pickupPoints), expected)
      && close(finalPickup.generator_points_final, expected) && close(settled.generator_points, expected)
      && finalPickup.points_status === (expected === awarded ? 'confirmado' : 'ajustado'),
    { esperados: expected, generator_points_final: finalPickup.generator_points_final,
      points_status: finalPickup.points_status, libro_recojo: pickupPoints.map(({ id, amount, type }) => ({ id, amount, type })) });
    // advance_challenges puede añadir recompensas: conciliar el saldo con TODO
    // el delta del libro, y verificar por separado el asiento de este recojo.
    check('Pendientes liberados y saldo conciliado',
      close(after.generator.points_pending, Math.max(0, number(before.generator.points_pending) - awarded))
      && close(after.generator.points_pending, state.initial.generator.points_pending)
      && number(after.generator.points_balance) > number(before.generator.points_balance)
      && close(number(after.generator.points_balance) - number(before.generator.points_balance), sum(points)),
    { antes: before.generator, despues: after.generator, pendientes_liberados: awarded,
      asientos_nuevos: points.map(({ id, type, amount, reference_type, reference_id }) => ({ id, type, amount, reference_type, reference_id })) });
    check('Entrega liquidada visible para rider', (await delivery(state.rider)).status === 'liquidada'
      && Boolean(settled.settled_at) && close(settled.total_kg, state.weight)
      && close(settled.gross_amount, state.weighed.subtotal) && close(settled.rider_amount, state.completed.rider_earnings),
    { codigo: settled.code, estado: settled.status, kg: settled.total_kg, bruto: settled.gross_amount,
      rider_amount: settled.rider_amount, settled_at: settled.settled_at });
    check('Acopio no paga dos veces al rider', newRows(before.earnings, after.earnings).length === 0
      && close(before.rider.earnings_balance, after.rider.earnings_balance)
      && close(before.rider.earnings_lifetime, after.rider.earnings_lifetime),
    { antes: before.rider, despues: after.rider });
    report('Contrato exacto de avisos del acopio', 'OMITIDO',
      'Avisos de las tres cuentas leídos en cada etapa; no hay definición local del trigger para exigir destinatarios/tipos concretos.');
  }],
];

console.log('| Paso | Resultado | Dato observado |');
console.log('| --- | --- | --- |');
let nextStep = 0;
try {
  config = await readConfig();
  report('Guardas de proyecto y clave pública', 'PASA', { url: config.url, cuentas: Object.values(ACCOUNTS).map(([email]) => email) });
  if (dryRun) {
    // No simula éxitos del backend: muestra el recorrido sin llamar a fetch.
    for (const [label] of steps) report(label, 'OMITIDO', '--dry-run: no ejecutado contra staging');
    nextStep = steps.length;
    requireThat(requests === 0, 'El modo sin red hizo peticiones');
    report('Validación local --dry-run', 'PASA', 'Configuración válida; 0 peticiones; recorrido preparado, no prueba el backend');
  } else {
    for (; nextStep < steps.length; nextStep += 1) {
      const [label, action] = steps[nextStep];
      try {
        const detail = await action();
        if (detail !== undefined) report(label, 'PASA', detail);
      } catch (error) {
        report(label, 'FALLA', error.message ?? 'Error sin detalle');
        nextStep += 1;
        break; // No repetir escrituras tras un error o timeout de resultado incierto.
      }
    }
  }
} catch (error) {
  report('Preparación', 'FALLA', error.message ?? 'Error sin detalle');
} finally {
  for (; nextStep < steps.length; nextStep += 1) report(steps[nextStep][0], 'OMITIDO', 'Dependencia anterior falló; cadena detenida');
  const counts = ['PASA', 'FALLA', 'OMITIDO'].map((status) => `${status}: ${results.filter((row) => row.resultado === status).length}`).join(', ');
  console.log(`\nResultado: ${process.exitCode ? 'FALLA' : dryRun ? 'DRY-RUN (sin verificar staging)' : 'PASA (ver omisiones)'}; ${counts}; peticiones HTTP: ${requests}.`);
  console.log(`Recojo creado: ${safe(state.pickup?.code ?? 'no disponible')} (id: ${safe(state.pickup?.id ?? 'no creado')}).`);
  console.log(`Entrega creada: ${safe(state.delivery?.code ?? 'no disponible')} (id: ${safe(state.delivery?.id ?? 'no creada')}).`);
  if (!dryRun) console.log('Los registros creados y la ubicación simulada del rider se conservan para rastrear la prueba.');
}
