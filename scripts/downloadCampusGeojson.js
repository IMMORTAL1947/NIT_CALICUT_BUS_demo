import { readFile, writeFile } from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

function parseArgs(argv) {
  const args = {
    code: 'NITC',
    out: 'export.geojson',
    buffer: 2000,
    overpass: 'https://overpass-api.de/api/interpreter'
  };
  for (const a of argv.slice(2)) {
    if (a.startsWith('--code=')) args.code = a.split('=')[1];
    else if (a.startsWith('--out=')) args.out = a.split('=')[1];
    else if (a.startsWith('--buffer=')) args.buffer = Number(a.split('=')[1]);
    else if (a.startsWith('--overpass=')) args.overpass = a.split('=')[1];
  }
  if (!Number.isFinite(args.buffer) || args.buffer <= 0) args.buffer = 2000;
  return args;
}

function metersToLat(m) {
  return m / 111320;
}

function metersToLng(m, lat) {
  const cos = Math.cos((lat * Math.PI) / 180);
  return m / (111320 * Math.max(cos, 0.1));
}

function computeBbox(stops, bufferMeters) {
  const lats = stops.map(s => Number(s.lat));
  const lngs = stops.map(s => Number(s.lng));
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const midLat = (minLat + maxLat) / 2;

  const padLat = metersToLat(bufferMeters);
  const padLng = metersToLng(bufferMeters, midLat);

  return {
    south: minLat - padLat,
    west: minLng - padLng,
    north: maxLat + padLat,
    east: maxLng + padLng
  };
}

async function loadStops(repoRoot, code) {
  const dataPath = path.join(repoRoot, 'server', 'data', 'data.json');
  const raw = await readFile(dataPath, 'utf8');
  const db = JSON.parse(raw);
  const college = db.colleges?.[code];
  if (!college) throw new Error(`College '${code}' not found in server/data/data.json`);
  const stops = (college.stops || []).filter(s => Number.isFinite(Number(s.lat)) && Number.isFinite(Number(s.lng)));
  if (stops.length === 0) throw new Error(`No valid stops for college '${code}'`);
  return { college, stops };
}

function buildOverpassQuery(bbox) {
  const { south, west, north, east } = bbox;
  return `
[out:json][timeout:180];
(
  way["highway"](${south},${west},${north},${east});
);
out tags geom;
`.trim();
}

function overpassToGeoJSON(overpassJson) {
  const features = [];
  for (const el of (overpassJson.elements || [])) {
    if (el.type !== 'way') continue;
    if (!Array.isArray(el.geometry) || el.geometry.length < 2) continue;
    const coords = el.geometry.map(p => [Number(p.lon), Number(p.lat)]);
    if (coords.some(([lng, lat]) => !Number.isFinite(lat) || !Number.isFinite(lng))) continue;

    features.push({
      type: 'Feature',
      properties: {
        '@id': `way/${el.id}`,
        ...(el.tags || {})
      },
      geometry: {
        type: 'LineString',
        coordinates: coords
      },
      id: `way/${el.id}`
    });
  }

  return {
    type: 'FeatureCollection',
    generator: 'overpass-api',
    timestamp: new Date().toISOString(),
    features
  };
}

async function main() {
  const args = parseArgs(process.argv);

  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);
  const repoRoot = path.resolve(__dirname, '..');

  const { college, stops } = await loadStops(repoRoot, args.code);
  const bbox = computeBbox(stops, args.buffer);
  const query = buildOverpassQuery(bbox);

  const res = await fetch(args.overpass, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    body: query
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Overpass request failed (${res.status}): ${text}`);
  }

  const overpassJson = await res.json();
  const geojson = overpassToGeoJSON(overpassJson);

  const outPath = path.isAbsolute(args.out) ? args.out : path.join(repoRoot, args.out);
  await writeFile(outPath, JSON.stringify(geojson, null, 2), 'utf8');

  console.log('Downloaded OSM GeoJSON for campus routing');
  console.log(` - College: ${college.name} (${college.code})`);
  console.log(` - Stops used: ${stops.length}`);
  console.log(` - Buffer: ${args.buffer}m`);
  console.log(` - Ways exported: ${geojson.features.length}`);
  console.log(` - Output: ${outPath}`);
}

main().catch((e) => {
  console.error('Error:', e.message);
  process.exit(1);
});
