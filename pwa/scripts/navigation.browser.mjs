// Local browser QA with synthetic data. All Supabase requests are intercepted.
// Start Vite on 5174. Set PLAYWRIGHT_MODULE to a local playwright/index.mjs if needed.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const output = new URL('../../.gstack/navigation-qa/', import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'msedge' });
const user = { id: '11111111-1111-4111-8111-111111111111', email: 'rider@example.invalid', role: 'authenticated', app_metadata: {}, user_metadata: {} };
const exp = Math.floor(Date.now() / 1000) + 3600;
const jwt = `${Buffer.from('{"alg":"HS256","typ":"JWT"}').toString('base64url')}.${Buffer.from(JSON.stringify({ sub: user.id, exp, role: user.role })).toString('base64url')}.fixture`;
const pickup = { id: 'pickup-fixture', status: 'en_route', rider_id: user.id, latitude: -12.046, longitude: -77.043,
  generator: { display_name: 'Familia Nureña', phone: '000000000' }, address: { address_line: 'Av. Nicolás de Piérola 560, Lima', reference: 'Puerta principal, frente al parque' }, pickup_items: [] };
const route = { code: 'Ok', routes: [{ distance: 2800, duration: 410, geometry: { coordinates: [[-77.034, -12.057], [-77.036, -12.055], [-77.039, -12.051], [-77.043, -12.046]] }, legs: [{ steps: [
  { distance: 400, name: 'Av. Nicolás de Piérola', maneuver: { type: 'depart', location: [-77.034, -12.057] } },
  { distance: 2400, name: 'Jirón de la Unión', maneuver: { type: 'turn', modifier: 'right', location: [-77.036, -12.055] } },
  { distance: 0, maneuver: { type: 'arrive', location: [-77.043, -12.046] } },
] }] }] };

try {
  for (const [name, width, height] of [['iphone', 390, 844], ['android', 360, 800], ['landscape', 844, 390], ['sin-gps', 390, 844], ['sin-ruta', 390, 844]]) {
    const context = await browser.newContext({ viewport: { width, height }, isMobile: true, hasTouch: true, geolocation: { latitude: -12.057, longitude: -77.034, accuracy: 10 }, permissions: ['geolocation'], serviceWorkers: 'block' });
    await context.addInitScript(({ jwt, user, exp }) => localStorage.setItem('sb-mbbdrmicjobydprvbpsj-auth-token', JSON.stringify({ access_token: jwt, refresh_token: 'fixture', token_type: 'bearer', expires_in: 3600, expires_at: exp, user })), { jwt, user, exp });
    if (name === 'sin-gps') await context.addInitScript(() => {
      const fail = (_ok, error) => { setTimeout(() => error({ code: 1, PERMISSION_DENIED: 1 }), 0); return 1; };
      Object.defineProperty(navigator, 'geolocation', { value: { watchPosition: fail, getCurrentPosition: fail, clearWatch() {} } });
    });
    await context.route('**/*.supabase.co/**', async (r) => {
      const url = new URL(r.request().url());
      const table = url.pathname.split('/').pop();
      const data = table === 'profiles' ? [{ role: 'eco_rider', display_name: 'Carlos' }]
        : table === 'rider_profiles' ? [{ vehicle_type: 'moto', is_available: false }]
        : table === 'pickups' ? (url.searchParams.get('rider_id')?.startsWith('eq.') ? [pickup] : [])
        : table === 'user' ? user : [];
      await r.fulfill({ json: data });
    });
    let attempts = 0;
    await context.route('**/routing.openstreetmap.de/**', async (r) => {
      attempts++;
      await r.fulfill(attempts === 1 || name === 'sin-ruta' ? { status: 503, json: { code: 'Unavailable' } } : { json: route });
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.goto('http://localhost:5174/r/navegar');
    await page.getByRole('heading', { name: 'Familia Nureña' }).waitFor();
    if (name === 'sin-gps') await page.getByText('Permite la ubicación precisa en los ajustes del navegador.').waitFor();
    else if (name === 'sin-ruta') await page.getByText('No pudimos actualizar la ruta. Reintentaremos automáticamente.').waitFor();
    else {
      await page.getByText('Gira a la derecha por Jirón de la Unión', { exact: true }).waitFor({ timeout: 20000 });
      assert.ok(attempts >= 2, 'stationary failure recovered');
    }
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'no horizontal overflow');
    assert.equal(await page.locator('.leaflet-overlay-pane path').count(), name.startsWith('sin-') ? 0 : 2, 'no invented geometry');
    const center = page.getByRole('button', { name: 'Centrar mapa en mi ubicación' });
    if (name === 'sin-gps') assert.equal(await center.isDisabled(), true);
    else await center.click();
    await page.screenshot({ path: new URL(`${name}.png`, output).pathname.replace(/^\/(\w:)/, '$1'), fullPage: true });
    assert.deepEqual(errors, []);
    console.log(`${name}: route state, drawing, recenter, layout and runtime passed`);
    await context.close();
  }
} finally { await browser.close(); }
