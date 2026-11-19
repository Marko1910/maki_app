/**
 * What each screen last loaded, for this session: coming back shows it at once while useAsync refreshes it quietly
 * (it used to wipe the screen to a skeleton on every visit). Memory only, so a reload starts clean, and signOut
 * clears it so one account never sees another's data. No imports: api.ts and util.ts both use it.
 */
const store = new Map<string, unknown>();

export const cacheGet = <T>(key: string) => store.get(key) as T | undefined;
export const cacheHas = (key: string) => store.has(key);
export const cacheSet = (key: string, value: unknown) => { store.set(key, value); };
export const cacheClear = () => store.clear();
