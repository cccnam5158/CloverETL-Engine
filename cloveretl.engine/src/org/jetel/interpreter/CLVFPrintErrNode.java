/* Generated By:JJTree: Do not edit this line. CLVFPrintErrNode.java */

package org.jetel.interpreter;

public class CLVFPrintErrNode extends SimpleNode {
  public CLVFPrintErrNode(int id) {
    super(id);
  }

  public CLVFPrintErrNode(FilterExpParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(FilterExpParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
