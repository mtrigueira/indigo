package indigo.scenes

import indigo.shared.BoundaryLocator
import indigo.shared.Context
import indigo.shared.datatypes.Rectangle
import indigo.shared.dice.Dice
import indigo.shared.events.InputState
import indigo.shared.input.Gamepad
import indigo.shared.input.Keyboard
import indigo.shared.input.Mouse
import indigo.shared.scenegraph.SceneNode
import indigo.shared.time.GameTime
import indigo.shared.time.Seconds

/** SceneContext is a Scene specific equivalent of `FrameContext`, and exposes all of the fields and methods or a normal
  * `FrameContext` object. It adds information about the scene currently running.
  *
  * @param sceneName
  *   The name of the current scene.
  * @param sceneStartTime
  *   The time that the current scene was entered.
  * @param frameContext
  *   The normal frame context object that all other fields delegate to.
  */
final class SceneContext[StartUpData](
    val sceneName: SceneName,
    val sceneStartTime: Seconds,
    val context: Context[StartUpData]
):
  export context.*

  /** The running time of the current scene calculated as the game's total running time minus time the scene was
    * entered.
    */
  lazy val sceneRunning: Seconds =
    context.frame.time.running - sceneStartTime

  def toFrameContext: Context[StartUpData] =
    context
