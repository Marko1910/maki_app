// Renders the app icons from the logo SVGs: npm run icons
import sharp from "sharp";
import { fileURLToPath } from "node:url";
const at = (p) => fileURLToPath(new URL(p, import.meta.url));
for (const [name, size] of [["icon-192.png", 192], ["icon-512.png", 512], ["apple-touch-icon.png", 180]]) {
  await sharp(at("../../design/ruma/logo/ruma-icon.svg"), { density: 600 }).resize(size, size).png().toFile(at(`../public/${name}`));
}
