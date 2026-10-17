# Third-party notices

This repository contains original code and documentation released under the MIT
license, plus development scaffolding derived from third-party projects.

- **Minecraft / Minecraft Forge**: Minecraft is a trademark of Mojang Synergies AB.
  This repository does not redistribute the Minecraft client or assets. The
  `lab/forge-1.20.1-test` and `lab/mcdev-probe` projects are built with the
  Minecraft Forge MDK / ForgeGradle; Forge and its MDK keep their own licenses
  (see the `LICENSE.txt` / upstream project).
- **Gradle wrapper**: the `gradle/wrapper/gradle-wrapper.jar` files come from the
  Gradle wrapper and are distributed under the Gradle project's license.
- **DeepSeek Harness**: the plugin targets DSH and uses its public plugin APIs;
  DSH packages keep their own licenses.
- **Agent Teams**: the plugin mounts the official DSH experimental Agent Teams
  bundle; it is experimental and carries no stability guarantee.
- **Mixin / MixinGradle**: used by the probe; see their upstream licenses.
- **Carpet: NeoForged**: only referenced in research notes; the jar is not
  redistributed in this repository.

No Minecraft assets (textures, sounds, language files) are copied into this
repository. The test block/item models reference vanilla texture paths at
runtime instead of shipping copies.
