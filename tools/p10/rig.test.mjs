import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { drawList, placeItem, pose } from './rig.mjs'

const catalogURL = new URL('../../assets/pets/catalog.json', import.meta.url)
const json = async url => JSON.parse(await readFile(url, 'utf8'))
const catalog = await json(catalogURL)
const point = ([a, b, c, d, x, y], [u, v]) => [a * u + c * v + x, b * u + d * v + y]

for (const entry of catalog.pets) {
  const manifest = new URL(entry.rig, catalogURL)
  const rig = await json(manifest)
  test(`${entry.id} exports its neutral pose, layer files and clips`, async () => {
    assert.equal(rig.id, entry.id)
    const still = pose(rig)
    const [width, height] = rig.canvas
    for (const sprite of [rig.neutral, ...still.layers, ...still.layers.flatMap(layer =>
      Object.values(layer.variants ?? {}))]) {
      const bytes = await readFile(new URL(sprite.src, manifest))
      assert.equal(bytes.toString('ascii', 0, 4), 'RIFF')
      assert.equal(bytes.toString('ascii', 8, 16), 'WEBPVP8L')
      const bits = bytes.readUInt32LE(21)
      assert.deepEqual([1 + (bits & 0x3fff), 1 + ((bits >>> 14) & 0x3fff)], sprite.size)
      const [x, y] = sprite.offset
      assert.ok(x >= 0 && x + sprite.size[0] <= width)
      assert.ok(y >= 0 && y + sprite.size[1] <= height)
    }
    for (const name of Object.keys(rig.clips)) {
      assert.deepEqual(pose(rig, name, 0).layers, still.layers)
      assert.deepEqual(pose(rig, name, rig.clips[name].duration).layers, still.layers)
    }
    for (const eye of ['eye_left', 'eye_right']) {
      const source = frame => frame.layers.find(layer => layer.bone === eye).src
      assert.equal(source(pose(rig, 'blink', 100)), rig.layers[eye].variants.closed.src)
      assert.equal(source(pose(rig, 'blink', 150)), rig.layers[eye].variants.half.src)
    }
  })

  test(`${entry.id} moves each equipment slot with its animated attachment`, () => {
    const still = pose(rig)
    const gesture = pose(rig, 'greet', 170)
    const fixture = {
      fits: {
        [rig.id]: Object.keys(rig.anchors).map(slot =>
          ({ slot, src: `fixture-${slot}.webp`, origin: [19, 27], offset: [3, 5] }))
      }
    }
    const result = drawList(rig, gesture, [fixture, { fits: {} }])
    assert.equal(result.length, gesture.layers.length + Object.keys(rig.anchors).length)
    for (const [slot, anchor] of Object.entries(rig.anchors)) {
      const piece = result.find(layer => layer.src === `fixture-${slot}.webp`)
      const expected = point(gesture.bones[anchor.bone], [anchor.position[0] + 3, anchor.position[1] + 5])
      const actual = point(piece.transform, [19, 27])
      assert.ok(Math.hypot(actual[0] - expected[0], actual[1] - expected[1]) < 0.000001)
      assert.equal(piece.depth, anchor.depth)
    }
    assert.equal(rig.anchors.wrist.bone, 'arm_right')
    assert.notDeepEqual(placeItem(rig, still, 'wrist'), placeItem(rig, gesture, 'wrist'))
    assert.deepEqual(result.map(layer => layer.depth), result.map(layer => layer.depth).sort((a, b) => a - b))
  })
}

test('the extended motion package resolves all sprite variants for all starter rigs', async () => {
  const motion = await json(new URL('../../assets/pets/motion.json', import.meta.url))
  for (const entry of catalog.pets) {
    const rig = await json(new URL(entry.rig, catalogURL))
    rig.clips = { ...motion, ...rig.clips }
    assert.equal(Object.keys(rig.clips).length, 21)
    for (const [name, clip] of Object.entries(rig.clips)) {
      for (const bone of Object.keys(clip.tracks ?? {})) assert.ok(rig.bones[bone], name + ' ' + bone)
      for (const [layer, keys] of Object.entries(clip.variants ?? {})) {
        for (const key of keys) {
          if (key.value === 'open') continue
          assert.ok(rig.layers[layer].variants[key.value], name + ' ' + key.value)
          assert.ok(pose(rig, name, key.time).layers.some(rendered =>
            rendered.src === rig.layers[layer].variants[key.value].src))
        }
      }
    }
    const still = placeItem(rig, pose(rig, 'eat', 0), 'wrist')
    const atTable = placeItem(rig, pose(rig, 'eat', 1040), 'wrist')
    assert.ok(atTable.transform[4] - still.transform[4] > 250)
  }
})
