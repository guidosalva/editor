package texteditor.commons

import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle

import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.UIManager

class JScrollableComponent extends JComponent with Scrollable {
  setFont(UIManager.getFont("TextField.font"))
  setCursor(new Cursor(Cursor.TEXT_CURSOR))

  val metrics = getFontMetrics(getFont)
  val unitHeight = metrics.getHeight
  val unitWidth = metrics.charWidth('m')

  override def setPreferredSize(preferredSize: Dimension) {
    super.setPreferredSize(preferredSize)
    if (getParent != null)
      getParent.doLayout
  }

  def getPreferredScrollableViewportSize = getPreferredSize

  def getScrollableTracksViewportHeight =
    getParent.isInstanceOf[JViewport] && getParent.asInstanceOf[JViewport].getHeight > getPreferredSize.height
  def getScrollableTracksViewportWidth =
    getParent.isInstanceOf[JViewport] && getParent.asInstanceOf[JViewport].getWidth > getPreferredSize.width

  def getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
    if (orientation == SwingConstants.HORIZONTAL) visibleRect.width else visibleRect.height

  def getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
    if (orientation == SwingConstants.HORIZONTAL) unitWidth else unitHeight
}
