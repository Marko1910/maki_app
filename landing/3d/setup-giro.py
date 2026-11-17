import bpy, math, os
S = bpy.context.scene

# --- un padre en el origen para que todo el fardo gire como una pieza ---
piv = bpy.data.objects.get("pivote_giro")
if piv is None:
    piv = bpy.data.objects.new("pivote_giro", None)
    S.collection.objects.link(piv)
piv.location = (0.0, 0.0, 0.0)
piv.rotation_euler = (0.0, 0.0, 0.0)

hijos = []
for ob in list(S.objects):
    if ob.type != 'MESH' or ob.name == "suelo_sombra":
        continue
    if ob.parent is not piv:
        mw = ob.matrix_world.copy()
        ob.parent = piv
        ob.matrix_parent_inverse = piv.matrix_world.inverted()
        ob.matrix_world = mw
    hijos.append(ob.name)

# --- ajustes de render para la secuencia ---
S.render.resolution_x = 1000
S.render.resolution_y = 900
S.render.resolution_percentage = 100
S.render.film_transparent = True
S.render.engine = 'CYCLES'
S.cycles.device = 'GPU'
S.cycles.samples = 56
S.cycles.use_denoising = True
try:
    S.cycles.denoiser = 'OPTIX'
except TypeError:
    S.cycles.denoiser = 'OPENIMAGEDENOISE'

formatos = [i.identifier for i in bpy.types.ImageFormatSettings.bl_rna.properties["file_format"].enum_items]
elegido = 'WEBP' if 'WEBP' in formatos else 'PNG'
S.render.image_settings.file_format = elegido
S.render.image_settings.color_mode = 'RGBA'
if elegido == 'WEBP':
    S.render.image_settings.quality = 88
else:
    S.render.image_settings.compression = 70

print("hijos:", len(hijos), "| formato:", elegido, "| de:", formatos)
print("camara:", tuple(round(v,3) for v in S.objects["camara"].location), "lente", S.objects["camara"].data.lens)
