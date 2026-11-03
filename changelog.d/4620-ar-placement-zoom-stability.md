<!-- category: Fixed -->
- **Demo app: Stabilize AR placement and pinch zoom.** Placement taps captured without active camera tracking are discarded, stopped-anchor recovery is limited to one safe attempt at the original pose, and pinch updates are coalesced per rendered frame. ARCore is optional at installation so Object Mode remains available on unsupported devices.
