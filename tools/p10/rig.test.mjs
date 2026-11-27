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
    const seated = pose(rig, 'sit', rig.clips.sit.holdFrom)
    const resting = pose(rig, 'sit', 950)
    const meal = pose(rig, 'eat', 0)
    const wipe = pose(rig, 'eat', 2200)
    assert.deepEqual(resting.bones, seated.bones)
    assert.equal(resting.layers.find(layer => layer.bone === 'eye_left').src, rig.layers.eye_left.variants.closed.src)
    assert.deepEqual(pose(rig, 'sit', rig.clips.sit.duration).layers, seated.layers)
    assert.deepEqual(meal.layers, seated.layers)
    assert.deepEqual(pose(rig, 'eat', rig.clips.eat.duration).layers, pose(rig).layers)
    assert.deepEqual(wipe.bones.root, seated.bones.root)
    assert.notDeepEqual(placeItem(rig, wipe, 'wrist'), placeItem(rig, seated, 'wrist'))
    assert.equal(wipe.layers.find(layer => layer.bone === 'mouth').src, rig.layers.mouth.variants.closed.src)
  }
})

test('glasses align with both eyes and the knit hat fits the neutral canvas', async () => {
  const wear = await json(new URL('items/catalog.json', catalogURL))
  const glasses = wear.items.find(item => item.id === 'round_glasses').render
  const hat = wear.items.find(item => item.id === 'knit_hat').render
  const lensX = [46.5, 156.5].map(center => center * glasses.displayWidth / 205)
  const lensY = glasses.displayWidth * 37.5 / 205
  for (const entry of catalog.pets) {
    const rig = await json(new URL(entry.rig, catalogURL))
    const frame = pose(rig)
    const glassesOrigin = [glasses.displayWidth * glasses.originX, glasses.displayWidth * 76 / 205 * glasses.originY]
    const face = placeItem(rig, frame, 'face', { origin: glassesOrigin }).transform
    for (const [index, name] of ['eye_left', 'eye_right'].entries()) {
      const [x, y] = point(face, [lensX[index], lensY])
      const [eyeX, eyeY] = rig.bones[name].pivot
      assert.ok(Math.hypot(x - eyeX, y - eyeY) < 8, `${entry.id} ${name}`)
    }
    const hatHeight = hat.displayWidth * 132 / 159
    const head = placeItem(rig, frame, 'head', {
      origin: [hat.displayWidth * hat.originX, hatHeight * hat.originY],
    }).transform
    for (const x of [0, hat.displayWidth]) {
      assert.ok(point(head, [x, 0])[1] > 0, `${entry.id} hat crosses the neutral canvas`)
    }
  }
})

test('all permitted catalogue outfits fit the room across the animated library', async t => {
  const catalogue = await json(new URL('items/catalog.json', catalogURL))
  const motion = await json(new URL('motion.json', catalogURL))
  const wear = await Promise.all(catalogue.items.filter(item => item.kind === 'wear' && item.enabled !== false).map(async item => {
    const file = await readFile(new URL(`items/${item.asset}`, catalogURL))
    assert.equal(file.toString('ascii', 8, 16), 'WEBPVP8L', item.asset)
    const bits = file.readUInt32LE(21)
    const width = item.render.displayWidth ?? 300
    const height = width * (1 + ((bits >>> 14) & 0x3fff)) / (1 + (bits & 0x3fff))
    return { ...item, width, height }
  }))
  let outfits = 0
  let poses = 0
  for (const entry of catalog.pets) {
    const rig = await json(new URL(entry.rig, catalogURL))
    rig.clips = { ...motion, ...rig.clips }
    const wardrobe = wear.filter(item => !item.compatiblePets.length || item.compatiblePets.includes(rig.id))
    const options = Object.keys(rig.anchors).map(slot => [null, ...wardrobe.filter(item => item.slots.includes(slot))])
    const worn = new Set()
    function countChoices(index) {
      if (index === options.length) {
        outfits++
        return
      }
      for (const item of options[index]) {
        if (item && worn.has(item.id)) continue
        if (item) worn.add(item.id)
        countChoices(index + 1)
        if (item) worn.delete(item.id)
      }
    }
    countChoices(0)
    const depth = slot => rig.anchors[slot].depth
    assert.equal(new Set(Object.keys(rig.anchors).map(depth)).size, options.length)
    assert.ok(depth('torso_base') < depth('torso_outer') && depth('torso_outer') < depth('neck'))
    assert.ok(depth('face') < depth('head'))

    // Each part has its own slot and depth. Its bounds and attachment are independent of other equipped parts.
    const pieces = Object.entries(rig.anchors).flatMap(([slot, anchor]) => wardrobe
      .filter(item => item.slots.includes(slot)).map(item => {
        const { width, height, render } = item
        const origin = [width * (render.originX ?? .5), height * (render.originY ?? .5)]
        const offset = [render.dx ?? 0, render.dy ?? 0]
        return { item, slot, anchor, origin, offset, corners: [[0, 0], [width, 0], [width, height], [0, height]] }
      }))
    const [canvasWidth, canvasHeight] = rig.canvas
    const roomHeight = 315
    const roomViews = [320, 394, 480, 600].map(screenWidth => {
      const roomWidth = screenWidth - 28
      const petHeight = roomHeight * .66
      const scale = Math.min(roomWidth * .63 / canvasWidth, petHeight / canvasHeight) * .89
      return {
        scale, roomWidth,
        left: (roomWidth - canvasWidth * scale) / 2,
        top: roomHeight - 30 - (petHeight + canvasHeight * scale) / 2,
      }
    })
    for (const [clip, { duration }] of [['', { duration: 0 }], ...Object.entries(rig.clips)]) {
      for (let time = 0; time <= duration; time = Math.min(duration, time + 10)) {
        const frame = pose(rig, clip, time)
        poses++
        for (const piece of pieces) {
          const { item, slot, anchor, origin, offset, corners } = piece
          const { transform } = placeItem(rig, frame, slot, { origin, offset, scale: item.render.scale, rotation: item.render.rotation })
          const [x, y] = point(transform, origin)
          const [anchorX, anchorY] = point(frame.bones[anchor.bone], [anchor.position[0] + offset[0], anchor.position[1] + offset[1]])
          assert.ok(Math.hypot(x - anchorX, y - anchorY) < 0.000001)
          for (const local of corners) {
            const [u, v] = point(transform, local)
            for (const view of roomViews) {
              const px = view.left + u * view.scale
              const py = view.top + v * view.scale
              if (px < 0 || px > view.roomWidth || py < 0 || py > roomHeight) {
                assert.fail(`${rig.id}/${item.id}, ${clip || 'neutral'} at ${time} ms, room ${view.roomWidth} dp: ${px},${py}`)
              }
            }
          }
        }
        if (time === duration) break
      }
    }
  }
  t.diagnostic(`${outfits} permitted outfits, including empty slots, and ${poses} poses from all five rigs`)
})
