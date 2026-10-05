# PPSSPP — core patcheado pelo Lemuroid

Os `libppsspp_libretro_android.so` deste módulo e do `bundled-cores` (4 ABIs) **não** são binário
do buildbot libretro: são o PPSSPP `eab2cc85bca530d47873c2015457cfa754a0115d` (`v1.19.3-987`,
2025-11-05) + os patches desta pasta, compilados à mão. A string de versão embutida no `.so` é
`v1.19.3-987-geab2cc85bc-lemuroid1` — aparece no logcat como `[BOOT] PPSSPP …`.

| Patch | Corrige |
|-------|---------|
| `0001-emuthreadstart-never-recreate-live-thread.patch` | `SIGABRT terminating` em `retro_run+1056` / `retro_serialize`: `EmuThreadStart` atribuía um `std::thread` novo sobre um vivo. Backport do upstream `72bd0fa4d3` + `b7d4a54a45` + `c42a3f070a`. Ver `Lemuroid/documentacao/bugs/*/2026-09-18-ppsspp-retro-run-terminate-abort.md`. |

## Por que não o nightly do upstream

A partir de `78ef1eae82` (2026-08-23) o upstream monta `flash0:` (fontes do PSP) em
`<saves>/PSP/NAND/flash0`, não mais em `<system>/PPSSPP/flash0`, onde o `PPSSPPAssetsManager` do
Lemuroid descompacta o `ppsspp.zip`. Trocar pelo nightly exige adaptar isso antes. Ao rebasear num
upstream mais novo, o patch 0001 sai — o upstream já tem a correção.

## Receita

O binário original (Swordfish90/LemuroidCores) foi feito por **ndk-build** em `libretro/jni` com
**NDK 27.3.13750724** — manter os dois.

```sh
git clone https://github.com/hrydgard/ppsspp.git && cd ppsspp
git checkout eab2cc85bca530d47873c2015457cfa754a0115d
git submodule update --init --recursive        # git submodule status sem + nem -
git apply <esta-pasta>/0001-*.patch
printf 'const char *PPSSPP_GIT_VERSION = "v1.19.3-987-geab2cc85bc-lemuroid1";\n#define PPSSPP_GIT_VERSION_NO_UPDATE 1\n' > git-version.cpp
cd libretro/jni
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  <NDK 27.3>/ndk-build -j16 APP_ABI=$abi APP_SHORT_COMMANDS=true \
    "APP_CPPFLAGS+=-fexceptions -frtti" \
    "APP_CFLAGS+=-ffile-prefix-map=$(pwd)/../..=ppsspp"
done
# saída: libretro/libs/<abi>/libretro.so -> copiar como libppsspp_libretro_android.so
```

- `-fexceptions -frtti` são obrigatórios (`LuaContext.cpp` usa `try`, `sceHttp.h` usa `dynamic_cast`);
  o binário original também tinha `.gcc_except_table`.
- `APP_SHORT_COMMANDS=true` só é necessário no Windows (o link estoura o limite de linha de comando,
  `Error 206`).
- `-ffile-prefix-map` tira do binário os caminhos absolutos da máquina de build. Conferir que o
  `.so` não contém `C:/`, `/home/` nem `/Users/`.

Mudou o patch → mudar o sufixo (`-lemuroid2`), para um tombstone/log identificar o binário.
