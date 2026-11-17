"""Finish Claude's saved geometry; run with Blender in background mode."""
import bpy
import math
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
bpy.ops.wm.open_mainfile(filepath=str(ROOT / '3d/ruma-fardo.blend'))
scene = bpy.context.scene

def material(name, color, roughness, metallic=0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    shader = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = roughness
    shader.inputs['Metallic'].default_value = metallic
    return mat, shader

cardboards = []
for i in range(5):
    mat, shader = material('Kraft %d' % i, (.32+i*.027, .17+i*.018, .075+i*.012), .86)
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    noise = nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 185
    noise.inputs['Detail'].default_value = 3
    bump = nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = .42
    bump.inputs['Distance'].default_value = .009
    links.new(noise.outputs['Fac'], bump.inputs['Height'])
    links.new(bump.outputs['Normal'], shader.inputs['Normal'])
    cardboards.append(mat)
steel, _ = material('Acero oscuro', (.095, .13, .115), .32, .8)
pet, shader = material('PET recuperado', (.16, .48, .34), .22)
shader.inputs['Transmission Weight'].default_value = .72
shader.inputs['IOR'].default_value = 1.46
for obj in list(scene.objects):
    if obj.type != 'MESH':
        bpy.data.objects.remove(obj, do_unlink=True)
        continue
    obj.data.materials.clear()
    if obj.name.startswith('carton'):
        obj.data.materials.append(cardboards[int(obj.name.split('_')[1]) % 5])
    elif obj.name.startswith('fleje'):
        obj.data.materials.append(steel)
        obj.location.z = .411
    else:
        obj.data.materials.append(pet)

def aim(obj, target=(0, 0, .4)):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat('-Z', 'Y').to_euler()

def light(name, loc, power, size, color):
    data = bpy.data.lights.new(name, 'AREA')
    data.energy, data.shape, data.size, data.color = power, 'DISK', size, color
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    obj.location = loc
    aim(obj)

light('Softbox principal', (1, -3, 4), 460, 3, (1, .88, .72))
light('Rebote', (-3, -1, 2), 310, 2.5, (.73, .88, 1))
light('Contraluz', (1, 3, 3), 600, 2, (.7, 1, .86))
camera = bpy.data.objects.new('Camera', bpy.data.cameras.new('Camera'))
scene.collection.objects.link(camera)
camera.location = (2.5, -3.5, 2.25)
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 1.92
aim(camera)
scene.camera = camera
scene.world.color = (.22, .22, .22)
scene.render.engine = 'CYCLES'
scene.cycles.samples = 40
scene.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons['cycles'].preferences
    prefs.compute_device_type = 'OPTIX'
    prefs.get_devices()
    for device in prefs.devices:
        device.use = device.type == 'OPTIX'
    scene.cycles.device = 'GPU'
except Exception:
    scene.cycles.device = 'CPU'
scene.render.resolution_x = 1200
scene.render.resolution_y = 1200
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'AgX'
scene.render.filepath = str(ROOT / '3d/ruma-fardo.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / '3d/ruma-fardo-final.blend'))
bpy.ops.render.render(write_still=True)
