package reswing

import java.awt.Dimension

import scala.swing.Action
import scala.swing.Button

import reswing.ImperativeSignal.toSignal

class ReButton(
    text: ImperativeSignal[String] = ImperativeSignal.noSignal,
    action: Action = null,
    minimumSize: ImperativeSignal[Dimension] = ImperativeSignal.noSignal,
    maximumSize: ImperativeSignal[Dimension] = ImperativeSignal.noSignal,
    preferredSize: ImperativeSignal[Dimension] = ImperativeSignal.noSignal)
  extends ReAbstractButton(
    text = text,
    minimumSize = minimumSize,
    maximumSize = maximumSize,
    preferredSize = preferredSize) {
  
  override protected lazy val peer = new Button(text getValue) with AbstractButtonMixin
  
  if (action != null)
    peer action = action
}

object ReButton {
  implicit def toButton(input : ReButton) : Button = input.peer
}
