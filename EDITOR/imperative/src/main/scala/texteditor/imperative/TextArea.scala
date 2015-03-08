package texteditor.imperative

import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.SystemColor
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

import scala.math.max
import scala.math.min
import scala.swing.Component
import scala.swing.Publisher
import scala.swing.event.Event
import scala.swing.event.Key
import scala.swing.event.KeyPressed
import scala.swing.event.KeyTyped
import scala.swing.event.MouseDragged
import scala.swing.event.MousePressed

import texteditor.commons.JScrollableComponent
import texteditor.commons.LineIterator
import texteditor.commons.LineOffset
import texteditor.commons.Position

case class CaretUpdate(val source: TextArea) extends Event

case class ValueChanged(val source: TextArea) extends Event

class TextArea extends Component with Publisher {
  override lazy val peer: JScrollableComponent = new JScrollableComponent with SuperMixin

  import peer.metrics.stringWidth
  import peer.{unitHeight => lineHeight}

  protected val padding = 5
  protected val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
  protected val buffer = new GapBuffer

  caret // force lazy object initialization

  def this(text: String) {
    this
    buffer.insert(text)
  }

  def charCount = buffer.length

  def lineCount = LineIterator(buffer.iterator).size

  def wordCount = buffer.iterator.foldLeft((0, false)){(c, ch) =>
    val alphanum = Character.isLetterOrDigit(ch)
    (if (alphanum && !c._2) c._1 + 1 else c._1, alphanum)}._1

  def selected = {
    val (dot, mark) = (caret.dot, caret.mark)
    val (start, end) = (min(dot, mark), max(dot, mark))
    buffer.iterator.slice(start, end)
  }

  def selectAll {
    caret.dot = charCount
    caret.mark = 0
  }

  def paste {
    removeSelection
    val c = clipboard.getContents(null);
    if (c.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      val str = c.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
      buffer.insert(str)
      caret.offset += str.length
      valueChanged
    }
  }

  def copy {
    if (selected.nonEmpty) {
      val s = new StringSelection(selected.mkString)
      clipboard.setContents(s, s)
    }
  }

  // A caret has a position in the document referred to as a dot.
  // The dot is where the caret is currently located in the model.
  // There is a second position maintained by the caret that represents
  // the other end of a selection called mark.
  // If there is no selection the dot and mark will be equal.
  // [same semantics as for: javax.swing.text.Caret]
  object caret extends Publisher {
    // dot as offset
    def dot = buffer.caret
    def dot_=(value: Int) =
      if (value != buffer.caret && value >= 0 && value <= buffer.length) {
        buffer.caret = value
        caretUpdated
      }

    // dot as position (row and column)
    def dotPos = LineOffset.position(buffer.iterator, dot)
    def dotPos_=(value: Position) =
      dot = LineOffset.offset(buffer.iterator, value)

    // mark as offset
    private var markOffset = 0
    def mark = markOffset
    def mark_=(value: Int) =
      if (value != markOffset && value >= 0 && value <= buffer.length) {
          markOffset = value
          caretUpdated
      }

    // mark as position (row and column)
    def markPos = LineOffset.position(buffer.iterator, mark)
    def markPos_=(value: Position) =
      mark = LineOffset.offset(buffer.iterator, value)

    // caret location as offset
    def offset = buffer.caret
    def offset_=(value: Int) =
      if ((value != buffer.caret || value != markOffset) && value >= 0 && value <= buffer.length) {
        buffer.caret = value
        markOffset = value
        caretUpdated
      }

    // caret location as position (row and column)
    def position = LineOffset.position(buffer.iterator, dot)
    def position_=(value: Position) =
      offset = LineOffset.offset(buffer.iterator, value)

    protected[TextArea] val blink = new Timer(500) start
    protected[TextArea] val steady = new Timer(500, false)
    protected[TextArea] var blinkVisible = false
    protected[TextArea] def visible = hasFocus && (steady.running || blinkVisible)
    blink.reactions += {
      case e @ TimerEvent(_) =>
        blinkVisible = !blinkVisible
        repaint
    }

    protected def caretUpdated {
      def it = LineIterator(buffer.iterator)
      preferredSize = new Dimension(2 * padding + it.map(stringWidth(_)).max, (it.size + 1) * lineHeight)

      val point = pointFromPosition(caret.dotPos)
      peer.scrollRectToVisible(new Rectangle(point.x - 8, point.y, 16, 2 * lineHeight))

      caret.steady.restart
      repaint

      publish(new CaretUpdate(TextArea.this))
    }
  }

  protected def posInLinebreak(p: Int) = p > 0 && p < buffer.length &&
    buffer(p - 1) == '\r' && buffer(p) == '\n'

  protected def removeSelection {
    val selStart = min(caret.dot, caret.mark)
    val selEnd = max(caret.dot, caret.mark)
    caret.offset = selStart
    buffer.remove(selEnd - selStart)
    valueChanged
  }

  protected def pointFromPosition(position: Position) = {
    val line = LineIterator(buffer.iterator).drop(position.row).next
    val y = position.row * lineHeight
    val x = stringWidth(line.substring(0, position.col))
    new Point(x + padding, y)
  }

  protected def positionFromPoint(point: Point) = {
    val row = point.y / lineHeight
    val it = LineIterator(buffer.iterator).drop(row)
    val col =
      if (it.hasNext) {
        var prefix = ""
        var col = 0
        it.next.takeWhile{ ch =>
          if (stringWidth(prefix + ch) < point.x) { prefix += ch; col += 1; true } else false }
        col
      }
      else 0
    Position(row, col)
  }

  listenTo(keys, mouse.clicks, mouse.moves);

  keys.reactions += {
    case e @ KeyPressed(_, _, _, _) =>
      def shift = e.modifiers == Key.Modifier.Shift
      if (e.modifiers == Key.Modifier.Control)
        e.key match {
          case Key.V => paste
          case Key.C => copy
          case Key.A => selectAll
          case _ =>
        }
      else
        e.key match {
          case Key.Left => // left arrow
            val offset = caret.offset - (if (posInLinebreak(caret.offset - 1)) 2 else 1)
            if (shift) caret.dot = offset else caret.offset = offset
          case Key.Right => // right arrow
            val offset = caret.offset + (if (posInLinebreak(caret.offset + 1)) 2 else 1)
            if (shift) caret.dot = offset else caret.offset = offset
          case Key.Up => // up arrow
            val position = Position(max(0, caret.position.row - 1), caret.position.col)
            if (shift) caret.dotPos = position else caret.position = position
          case Key.Down => // down arrow
            val position = Position(min(lineCount - 1, caret.position.row + 1), caret.position.col)
            if (shift) caret.dotPos = position else caret.position = position
          case Key.Home =>
            var offset = 0
            for ((ch, i) <- buffer.iterator.zipWithIndex)
              if (i < caret.offset && (ch == '\r' || ch == '\n'))
                offset = i + 1;
            if (shift) caret.dot = offset else caret.offset = offset
          case Key.End =>
            val offset =
              caret.offset +
	            buffer.iterator.drop(caret.offset).takeWhile{
	              ch => ch != '\r' && ch != '\n'
	            }.size
	        if (shift) caret.dot = offset else caret.offset = offset
          case _ =>
        }
    case e @ KeyTyped(_, _, _, _) =>
      if (e.modifiers != Key.Modifier.Control) {
        e.char match {
          case '\u007f' => // Del key
            if (selected.isEmpty) {
              val count = if (posInLinebreak(caret.dot + 1)) 2 else 1
              buffer.remove(count);
            }
            else removeSelection
            valueChanged
          case '\b' => // Backspace key
            if (selected.isEmpty) {
              val count = min(if (posInLinebreak(caret.dot - 1)) 2 else 1, caret.dot)
              caret.offset -= count
              buffer.remove(count);
            }
            else removeSelection
            valueChanged
          case c => // character input
            removeSelection
            buffer.insert(c.toString)
            caret.offset += 1
            valueChanged
        }
      }
  }

  mouse.clicks.reactions += {
    case e @ MousePressed(_, _, _, _, _) =>
      this.requestFocusInWindow
      caret.position = positionFromPoint(e.point)
  }

  mouse.moves.reactions += {
    case e @ MouseDragged(_, _, _) =>
      caret.dotPos = positionFromPoint(e.point)
  }

  protected def valueChanged {
    caret.steady.restart
    repaint
    publish(new ValueChanged(this))
  }

  override def paintComponent(g: Graphics2D) {
    super.paintComponent(g)
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    g.setColor(SystemColor.text)
    g.fillRect(0, 0, size.width, size.height + lineHeight)

    val selStart = min(caret.dot, caret.mark)
    val selEnd = max(caret.dot, caret.mark)

    var lineIndex = 0
    var charIndex = 0
    for (line <- LineIterator(buffer.iterator)) {
      var start, middle, end = ""
      var middleX, endX = 0

      if (selStart < charIndex + line.length && selEnd > charIndex) {
        val startIndex = if (selStart > charIndex) selStart - charIndex else 0
        val endIndex = if (selEnd < charIndex + line.length) selEnd - charIndex else line.length

        start = line.substring(0, startIndex)
        middle = line.substring(startIndex, endIndex)
        end = line.substring(endIndex)

        middleX = padding + stringWidth(start)
        endX = padding + stringWidth(start + middle)

        g.setColor(SystemColor.textHighlight)
        g.fillRect(middleX, lineIndex * lineHeight + lineHeight - this.font.getSize, endX - middleX, lineHeight)
      }
      else
        start = line

      lineIndex += 1
      charIndex += line.length

      g.setColor(SystemColor.textText)
      g.drawString(start, padding, lineIndex * lineHeight)
      g.drawString(end, endX, lineIndex * lineHeight)

      g.setColor(SystemColor.textHighlightText)
      g.drawString(middle, middleX, lineIndex * lineHeight)
    }

    if (caret.visible) {
      def point = pointFromPosition(caret.position)
      g.setColor(SystemColor.textText)
      g.drawLine(point.x, point.y + lineHeight - this.font.getSize, point.x, point.y + lineHeight)
    }
  }
}
