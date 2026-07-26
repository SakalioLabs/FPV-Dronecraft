# Official Minecraft/Fabric audio boundary

Date: 2026-07-24  
Audited versions: Minecraft `1.21.11`, Fabric Loader `0.19.3`,
Fabric API `0.141.4+1.21.11`, `fabric-sound-api-v1`
`1.0.51+4fc5413f3e`.

## Decision

The supported low-risk integration is the one already used by Dronecraft:

1. keep Minecraft's positional `SoundInstance`, linear attenuation, category
   volume, voice allocation and device/resource lifecycle;
2. implement Fabric's public `FabricSoundInstance#getAudioStream`;
3. return a custom `AudioStream` that generates phase-continuous PCM;
4. keep OpenAL pitch at `1.0`, because moving the complete PCM stream would
   incorrectly pitch-shift broadband noise together with tracked orders;
5. encode the three-band DDA transmission in generated PCM;
6. use one additional listener-relative custom stream for a shared late-reverb
   wet bus instead of copying an FDN per positional source;
7. switch to clean-source mode when another mod owns occlusion/reverb.

This is the useful official simplification: Minecraft remains the spatial
voice host while Dronecraft supplies samples. It avoids private OpenAL source
IDs, channel mixins and resource/device reload hooks.

## Audited API evidence

The exact cached Fabric sound API source archive has SHA-256:

```text
ee684b4499131e44dd81cc92e6a399393e5ce50035e9192c4170e4f403b13bb9
```

Its only public client interface in this module is `FabricSoundInstance`.
`getAudioStream(loader, id, repeatInstantly)` defaults to the normal resource
stream and is explicitly documented for custom streams driven by network or
user input. Fabric's `SoundEngineMixin` redirects only the stream-loading call
to this method. The audited module exposes no per-source filter, EFX slot,
reverb-send or OpenAL-channel lifecycle API.

Therefore:

- custom PCM streaming is public and versioned;
- source positioning/attenuation through `SoundInstance` is public;
- direct OpenAL EFX attachment is not part of this Fabric API contract;
- an EFX experiment would require separate capability detection and lifecycle
  integration and must remain disabled by default until runClient evidence
  covers reload and device replacement.

## Current project mapping

- `DroneLoopSoundInstance` implements `FabricSoundInstance`, stays positional,
  selects `LINEAR` attenuation and returns `ProceduralDroneAudioStream`.
- `ProceduralDroneAudioStream` feeds the official streaming path.
- Experimental `ListenerReverbSoundInstance` uses the same public
  `getAudioStream` boundary for exactly one listener-relative wet bus. Its
  `ListenerReverbAudioStream` pre-mixes active drone emission snapshots and
  runs one eight-line FDN; it is property-gated and disabled by default.
- Ogg loops remain packaged for the property-controlled vanilla fallback;
  replacing them with Fabric's empty placeholder would remove that recovery
  path and is therefore rejected.
- `PropagationCompatibilityPolicy` uses Fabric Loader's stable mod-id query.
  In `auto`, Sound Physics Remastered becomes the sole environmental
  propagation owner while source synthesis/directivity/Doppler remain active.

## Remaining runtime evidence

The API audit and unit/build checks do not prove audible behavior. Before
release, the runClient acoustic lab must record:

- start/stop, world unload and entity removal;
- resource reload and audio-device replacement;
- procedural-to-Ogg fallback;
- internal versus clean-source A/B;
- no voice leak or stream underrun;
- no double attenuation with Sound Physics Remastered.
- one shared wet voice for 1–6 drones, continued tail after source removal,
  and no tail leak across world unload.

## Sources

- [Fabric Sound API source](https://github.com/FabricMC/fabric/tree/1.21.11/fabric-sound-api-v1/src/client/java/net/fabricmc/fabric/api/client/sound/v1)
- [Fabric project structure and stable mod id](https://github.com/FabricMC/fabric-docs/blob/main/develop/getting-started/project-structure.md)
- [Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered)
