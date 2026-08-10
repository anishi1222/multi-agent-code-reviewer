# Default Discovery Needs Discriminating Smoke Controls

Commands that discover configured defaults must be tested from a populated working directory and assert known entries, not accept either an empty or populated result.

## What Happened

In rearchitecture/t22, 1,110 tests passed while packaged `list` still returned `No agents found.`
from a repository containing nine valid agents. An early return in `LoadAgentUseCase` prevented the
adapter from merging configured defaults. Separately, 32 parsed skills never reached the DI registry
used by `skill --list`. Smoke assertions accepted both empty and populated output, masking both
composition defects.

## Takeaway

For default-plus-override boundaries, pass empty add-ons through to the configuration-owning adapter;
do not interpret empty add-ons as “no configured inputs.” Pair runtime controls: no explicit option
must discover known defaults, while an explicit option must add or override predictably. Assert
specific fixture names/counts so an empty inventory cannot pass as startup success.

## History

- 2026-08-09 (rearchitecture/t22): initial
