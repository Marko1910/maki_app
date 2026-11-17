import bpy, math, time, os
S = bpy.context.scene
OUT = r"C:/Users/marco/AppData/Local/Temp/claude/D--proyects0-0-Maki-app-MAKI/14b5722a-0f46-4a53-b1a4-ae4eb33b46ab/scratchpad/giro/"
N = 36

def giro(a, b):
    S.cycles.samples = 56
    S.render.resolution_x, S.render.resolution_y = 900, 810
    S.render.image_settings.quality = 68
    piv = bpy.data.objects["pivote_giro"]
    t0 = time.time()
    for i in range(a, b):
        piv.rotation_euler = (0.0, 0.0, i * 2.0 * math.pi / N)
        S.render.filepath = OUT + "f%02d" % i
        bpy.ops.render.render(write_still=True)
    hechos = sorted(f for f in os.listdir(OUT) if f.endswith(".webp"))
    kb = sum(os.path.getsize(OUT + f) for f in hechos) // 1024
    return "cuadros %d-%d en %.0f s | %d de %d | %d KB" % (a, b - 1, time.time() - t0, len(hechos), N, kb)
