/*
 *    jETeL/Clover.ETL - Java based ETL application framework.
 *    Copyright (C) 2002-2004  David Pavlis <david_pavlis@hotmail.com>
 *    
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *    
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
 *    Lesser General Public License for more details.
 *    
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.jetel.interpreter;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;

import org.jetel.interpreter.node.*;

import org.jetel.data.primitive.DecimalFactory;
import org.jetel.data.DataField;
import org.jetel.data.DataRecord;
import org.jetel.data.Numeric;
import org.jetel.exception.BadDataFormatException;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.data.primitive.CloverDouble;
import org.jetel.data.primitive.CloverInteger;
import org.jetel.data.primitive.CloverLong;
import org.jetel.util.Compare;

/**
 * Executor of FilterExpression parse tree.
 * 
 * @author dpavlis
 * @since 16.9.2004
 * 
 * Executor of FilterExpression parse tree
 */
public class TransformLangExecutor implements TransformLangParserVisitor,
        TransformLangParserConstants{

    public static final int BREAK_BREAK=1;
    public static final int BREAK_CONTINUE=2;
    public static final int BREAK_RETURN=2;
    
    protected Stack stack;

    protected boolean breakFlag;
    protected int breakType;
    protected Properties globalParameters;
    
    protected DataRecord[] inputRecords;
    protected DataRecord[] outputRecords;

    /**
     * Constructor
     */
    public TransformLangExecutor(Properties globalParameters) {
        stack = new Stack();
        breakFlag = false;
        this.globalParameters=globalParameters;
    }
    
    public TransformLangExecutor() {
        this(null);
    }
    
    public void setInputRecords(DataRecord[] inputRecords){
        this.inputRecords=inputRecords;
    }
    
    public void setOutputRecords(DataRecord[] outputRecords){
        this.outputRecords=outputRecords;
    }
    
    /**
     * Method which returns result of executing parse tree.<br>
     * Basically, it returns whatever object was left on top of executor's
     * stack.
     * 
     * @return
     */
    public Object getResult() {
        return stack.pop();
    }

    /* *********************************************************** */

    /* implementation of visit methods for each class of AST node */

    /* *********************************************************** */
    /* it seems to be necessary to define a visit() method for SimpleNode */

    public Object visit(SimpleNode node, Object data) {
        throw new TransformLangExecutorRuntimeException(node,
                "Error: Call to visit for SimpleNode");
    }

    public Object visit(CLVFStart node, Object data) {

        int i, k = node.jjtGetNumChildren();

        for (i = 0; i < k; i++)
            node.jjtGetChild(i).jjtAccept(this, data);

        return data; // this value is ignored in this example
    }
    
    public Object visit(CLVFStartExpression node, Object data) {

        int i, k = node.jjtGetNumChildren();

        for (i = 0; i < k; i++)
            node.jjtGetChild(i).jjtAccept(this, data);

        return data; // this value is ignored in this example
    }


    public Object visit(CLVFOr node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        if (((Boolean) (stack.pop())).booleanValue()) {
            stack.push(Stack.TRUE_VAL);
            return data;
        }

        node.jjtGetChild(1).jjtAccept(this, data);

        if (((Boolean) stack.pop()).booleanValue()) {
            stack.push(Stack.TRUE_VAL);

        } else {
            stack.push(Stack.FALSE_VAL);
        }
        return data;
    }

    public Object visit(CLVFAnd node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        if (!((Boolean) (stack.pop())).booleanValue()) {
            stack.push(Stack.FALSE_VAL);
            return data;
        }

        node.jjtGetChild(1).jjtAccept(this, data);

        if (!((Boolean) (stack.pop())).booleanValue()) {
            stack.push(Stack.FALSE_VAL);
            return data;
        } else {
            stack.push(Stack.TRUE_VAL);
            return data;
        }
    }

    public Object visit(CLVFComparison node, Object data) {
        int cmpResult = 2;
        boolean lValue = false;
        // special handling for Regular expression
        if (node.cmpType == REGEX_EQUAL) {
            node.jjtGetChild(0).jjtAccept(this, data);
            Object field1 = stack.pop();
            node.jjtGetChild(1).jjtAccept(this, data);
            Object field2 = stack.pop();
            if (field1 instanceof CharSequence && field2 instanceof Matcher) {
                Matcher regex = (Matcher) field2;
                regex.reset(((CharSequence) field1));
                if (regex.matches()) {
                    lValue = true;
                } else {
                    lValue = false;
                }
            } else {
                Object[] arguments = { field1, field2 };
                throw new TransformLangExecutorRuntimeException(arguments,
                        "regex equal - wrong type of literal(s)");
            }

            // other types of comparison
        } else {
            node.jjtGetChild(0).jjtAccept(this, data);
            Object a = stack.pop();
            node.jjtGetChild(1).jjtAccept(this, data);
            Object b = stack.pop();

            try {
                if (a instanceof Numeric && b instanceof Numeric) {
                    cmpResult = ((Numeric) a).compareTo((Numeric) b);
                    /*
                     * }else if (a instanceof Number && b instanceof Number){
                     * cmpResult=Compare.compare((Number)a,(Number)b);
                     */
                } else if (a instanceof Date && b instanceof Date) {
                    cmpResult = ((Date) a).compareTo((Date) b);
                } else if (a instanceof CharSequence
                        && b instanceof CharSequence) {
                    cmpResult = Compare.compare((CharSequence) a,
                            (CharSequence) b);
                } else if (a instanceof Boolean && b instanceof Boolean) {
                    cmpResult = ((Boolean) a).equals(b) ? 0 : -1;
                } else {
                    Object arguments[] = { a, b };
                    throw new TransformLangExecutorRuntimeException(arguments,
                            "compare - incompatible literals/expressions");
                }
            } catch (ClassCastException ex) {
                Object arguments[] = { a, b };
                throw new TransformLangExecutorRuntimeException(arguments,
                        "compare - incompatible literals/expressions");
            }
            switch (node.cmpType) {
            case EQUAL:
                if (cmpResult == 0) {
                    lValue = true;
                }
                break;// equal
            case LESS_THAN:
                if (cmpResult == -1) {
                    lValue = true;
                }
                break;// less than
            case GREATER_THAN:
                if (cmpResult == 1) {
                    lValue = true;
                }
                break;// grater than
            case LESS_THAN_EQUAL:
                if (cmpResult <= 0) {
                    lValue = true;
                }
                break;// less than equal
            case GREATER_THAN_EQUAL:
                if (cmpResult >= 0) {
                    lValue = true;
                }
                break;// greater than equal
            case NON_EQUAL:
                if (cmpResult != 0) {
                    lValue = true;
                }
                break;
            default:
                // this should never happen !!!
                throw new RuntimeException("Unsupported cmparison operator !");
            }
        }
        stack.push(lValue ? Stack.TRUE_VAL : Stack.FALSE_VAL);
        return data;
    }

    public Object visit(CLVFAddNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);

        Object b = stack.pop();
        Object a = stack.pop();

        if (a == null || b == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { a, b },
                    "NULL value not allowed");
        }

        if (!(b instanceof Numeric || b instanceof CharSequence)) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { b },
                    "add - wrong type of literal");
        }

        try {
            if (a instanceof Numeric) {
                Numeric result = ((Numeric) a).duplicateNumeric();
                result.add((Numeric) b);
                stack.push(result);
            } else if (a instanceof Date) {
                Calendar result = Calendar.getInstance();
                result.setTime((Date) a);
                result.add(Calendar.DATE, ((Numeric) b).getInt());
                stack.push(result.getTime());
            } else if (a instanceof CharSequence) {
                StringBuffer buf;
                CharSequence a1 = (CharSequence) a;
                if (b instanceof CharSequence) {
                    CharSequence b1 = (CharSequence) b;
                    buf = new StringBuffer(a1.length() + b1.length());
                    for (int i = 0; i < a1.length(); buf.append(a1.charAt(i++)))
                        ;
                    for (int i = 0; i < b1.length(); buf.append(b1.charAt(i++)))
                        ;
                } else {
                    buf = new StringBuffer(a1.length() + 10);
                    for (int i = 0; i < a1.length(); buf.append(a1.charAt(i++)))
                        ;
                    buf.append(b);
                }
                stack.push(buf);
            } else {
                Object[] arguments = { a, b };
                throw new TransformLangExecutorRuntimeException(arguments,
                        "add - wrong type of literal(s)");
            }
        } catch (ClassCastException ex) {
            Object arguments[] = { a, b };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "add - wrong type of literal(s)");
        }

        return data;
    }

    public Object visit(CLVFSubNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);

        Object b = stack.pop();
        Object a = stack.pop();

        if (a == null || b == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { a, b },
                    "NULL value not allowed");
        }

        if (!(b instanceof Number)) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { b },
                    "sub - wrong type of literal");
        }

        if (a instanceof Numeric) {
            Numeric result = ((Numeric) a).duplicateNumeric();
            result.sub((Numeric) b);
            stack.push(result);
        } else if (a instanceof Date) {
            Calendar result = Calendar.getInstance();
            result.setTime((Date) a);
            result.add(Calendar.DATE, ((Numeric) b).getInt() * -1);
            stack.push(result.getTime());
        } else {
            Object[] arguments = { a, b };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "sub - wrong type of literal(s)");
        }

        return data;
    }

    public Object visit(CLVFMulNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);

        Object b = stack.pop();
        Object a = stack.pop();

        if (a == null || b == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { a, b },
                    "NULL value not allowed");
        }

        if (!(b instanceof Number)) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { b },
                    "mul - wrong type of literal");
        }

        if (a instanceof Numeric) {
            Numeric result = ((Numeric) a).duplicateNumeric();
            result.mul((Numeric) b);
            stack.push(result);
        } else {
            Object[] arguments = { a, b };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "mul - wrong type of literal(s)");
        }
        return data;
    }

    public Object visit(CLVFDivNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);

        Object b = stack.pop();
        Object a = stack.pop();

        if (a == null || b == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { a, b },
                    "NULL value not allowed");
        }

        if (!(b instanceof Number)) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { b },
                    "div - wrong type of literal");
        }

        if (a instanceof Numeric) {
            Numeric result = ((Numeric) a).duplicateNumeric();
            result.div((Numeric) b);
            stack.push(result);
        } else {
            Object[] arguments = { a, b };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "div - wrong type of literal(s)");
        }

        return data;
    }

    public Object visit(CLVFModNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);
        Object b = stack.pop();
        Object a = stack.pop();

        if (a == null || b == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { a, b },
                    "NULL value not allowed");
        }

        if (!(b instanceof Number)) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { b },
                    "mod - wrong type of literal");
        }

        if (a instanceof Numeric) {
            Numeric result = ((Numeric) a).duplicateNumeric();
            result.mod((Numeric) b);
            stack.push(result);
        } else {
            Object[] arguments = { a, b };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "mod - wrong type of literal(s)");
        }

        return data;
    }

    public Object visit(CLVFNegation node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object value = stack.pop();

        if (value instanceof Boolean) {
            stack.push(((Boolean) value).booleanValue() ? Stack.FALSE_VAL
                    : Stack.TRUE_VAL);
        } else {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { stack
                    .get() }, "NULL value not allowed");
        }

        return data;
    }

    public Object visit(CLVFSubStrNode node, Object data) {
        int length, from;

        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);
        node.jjtGetChild(2).jjtAccept(this, data);
        Object lengthO = stack.pop();
        Object fromO = stack.pop();
        Object str = stack.pop();

        if (lengthO == null || fromO == null || str == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { lengthO,
                    fromO, str }, "NULL value not allowed");
        }

        try {
            length = ((Numeric) lengthO).getInt();
            from = ((Numeric) fromO).getInt();
        } catch (Exception ex) {
            Object arguments[] = { lengthO, fromO, str };
            throw new TransformLangExecutorRuntimeException(arguments, "substring - "
                    + ex.getMessage());
        }

        if (str instanceof CharSequence) {
            stack.push(((CharSequence) str).subSequence(from, from + length));

        } else {
            Object[] arguments = { lengthO, fromO, str };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "substring - wrong type of literal(s)");
        }

        return data;
    }

    public Object visit(CLVFUppercaseNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object a = stack.pop();

        if (a instanceof CharSequence) {
            CharSequence seq = (CharSequence) a;
            node.strBuf.setLength(0);
            node.strBuf.ensureCapacity(seq.length());
            for (int i = 0; i < seq.length(); i++) {
                node.strBuf.append(Character.toUpperCase(seq.charAt(i)));
            }
            stack.push(node.strBuf);
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "uppercase - wrong type of literal");
        }

        return data;
    }

    public Object visit(CLVFLowercaseNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        Object a = stack.pop();

        if (a instanceof CharSequence) {
            CharSequence seq = (CharSequence) a;
            node.strBuf.setLength(0);
            node.strBuf.ensureCapacity(seq.length());
            for (int i = 0; i < seq.length(); i++) {
                node.strBuf.append(Character.toLowerCase(seq.charAt(i)));
            }
            stack.push(node.strBuf);
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "lowercase - wrong type of literal");
        }
        return data;
    }

    public Object visit(CLVFTrimNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        Object a = stack.pop();
        int start, end;

        if (a instanceof CharSequence) {
            CharSequence seq = (CharSequence) a;
            int length = seq.length();
            for (start = 0; start < length; start++) {
                if (seq.charAt(start) != ' ' && seq.charAt(start) != '\t') {
                    break;
                }
            }
            for (end = length - 1; end >= 0; end--) {
                if (seq.charAt(end) != ' ' && seq.charAt(end) != '\t') {
                    break;
                }
            }
            if (start > end)
                stack.push("");
            else
                stack.push(seq.subSequence(start, end + 1));
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "trim - wrong type of literal");
        }
        return data;
    }

    public Object visit(CLVFLengthNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        Object a = stack.pop();

        if (a instanceof CharSequence) {
            stack.push(new CloverInteger(((CharSequence) a).length()));
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "lenght - wrong type of literal");
        }

        return data;
    }

    public Object visit(CLVFTodayNode node, Object data) {
        java.util.Date today = Calendar.getInstance().getTime();
        stack.push(today);

        return data;
    }

    public Object visit(CLVFIsNullNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object value = stack.pop();

        if (value == null) {
            stack.push(Stack.TRUE_VAL);
        } else {
            stack.push(Stack.FALSE_VAL);
        }

        return data;
    }

    public Object visit(CLVFNVLNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object value = stack.pop();

        if (value == null) {
            node.jjtGetChild(1).jjtAccept(this, data);
            // not necessary: stack.push(stack.pop());
        } else {
            stack.push(value);
        }

        return data;
    }

    public Object visit(CLVFLiteral node, Object data) {
        stack.push(node.value);
        return data;
    }

    public Object visit(CLVFInputFieldLiteral node, Object data) {
        DataField field=inputRecords[node.recordNo].getField(node.fieldNo);
        if (field instanceof Numeric){
            stack.push(((Numeric)field).duplicateNumeric());
        }else{
            stack.push(field.getValue());
        }
        
        // old stack.push(inputRecords[node.recordNo].getField(node.fieldNo).getValue());
        
        // we return reference to DataField so we can
        // perform extra checking in special cases
        return node.field;
    }

    public Object visit(CLVFOutputFieldLiteral node, Object data) {
        //stack.push(inputRecords[node.recordNo].getField(node.fieldNo));
        // we return reference to DataField so we can
        // perform extra checking in special cases
        return data;
    }
    
    public Object visit(CLVFGlobalParameterLiteral node, Object data) {
        stack.push(globalParameters!=null ? globalParameters.getProperty(node.name) : null);
        return data;
    }
    
    public Object visit(CLVFRegexLiteral node, Object data) {
        stack.push(node.matcher);
        return data;
    }

    public Object visit(CLVFConcatNode node, Object data) {
        Object a;
        StringBuffer strBuf = new StringBuffer(40);
        int numChildren = node.jjtGetNumChildren();
        for (int i = 0; i < numChildren; i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
            a = stack.pop();

            if (a instanceof CharSequence) {
                CharSequence seqA = (CharSequence) a;
                strBuf.ensureCapacity(strBuf.length() + seqA.length());
                for (int j = 0; j < seqA.length(); j++) {
                    strBuf.append(seqA.charAt(j));
                }
            } else {
                if (a != null) {
                    strBuf.append(a);
                } else {
                    Object[] arguments = { a };
                    throw new TransformLangExecutorRuntimeException(arguments,
                            "concat - wrong type of literal(s)");
                }
            }
        }
        stack.push(strBuf);
        return data;
    }

    public Object visit(CLVFDateAddNode node, Object data) {
        int shiftAmount;

        node.jjtGetChild(0).jjtAccept(this, data);
        Object date = stack.pop();
        node.jjtGetChild(1).jjtAccept(this, data);
        Object amount = stack.pop();

        try {
            shiftAmount = ((Numeric) amount).getInt();
        } catch (Exception ex) {
            Object arguments[] = { amount };
            throw new TransformLangExecutorRuntimeException(arguments, "dateadd - "
                    + ex.getMessage());
        }
        if (date instanceof Date) {
            node.calendar.setTime((Date) date);
            node.calendar.add(node.calendarField, shiftAmount);
            stack.push(node.calendar.getTime());
        } else {
            Object arguments[] = { date };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "dateadd - no Date expression");
        }

        return data;
    }

    public Object visit(CLVFDateDiffNode node, Object data) {
        Object date1, date2;

        node.jjtGetChild(0).jjtAccept(this, data);
        date1 = stack.pop();
        node.jjtGetChild(1).jjtAccept(this, data);
        date2 = stack.pop();

        if (date1 instanceof Date && date2 instanceof Date) {
            long diffSec = (((Date) date1).getTime() - ((Date) date2).getTime()) / 1000;
            int diff = 0;
            switch (node.calendarField) {
            case Calendar.SECOND:
                // we have the difference in seconds
                diff = (int) diffSec;
                break;
            case Calendar.MINUTE:
                // how many minutes'
                diff = (int) diffSec / 60;
                break;
            case Calendar.HOUR_OF_DAY:
                diff = (int) diffSec / 3600;
                break;
            case Calendar.DAY_OF_MONTH:
                // how many days is the difference
                diff = (int) diffSec / 86400;
                break;
            case Calendar.WEEK_OF_YEAR:
                // how many weeks
                diff = (int) diffSec / 604800;
                break;
            case Calendar.MONTH:
                node.start.setTime((Date) date1);
                node.end.setTime((Date) date2);
                diff = (node.start.get(Calendar.MONTH) + node.start
                        .get(Calendar.YEAR) * 12)
                        - (node.end.get(Calendar.MONTH) + node.end
                                .get(Calendar.YEAR) * 12);
                break;
            case Calendar.YEAR:
                node.start.setTime((Date) date1);
                node.end.setTime((Date) date2);
                diff = node.start.get(node.calendarField)
                        - node.end.get(node.calendarField);
                break;
            default:
                Object arguments[] = { new Integer(node.calendarField) };
                throw new TransformLangExecutorRuntimeException(arguments,
                        "datediff - wrong difference unit");
            }
            stack.push(new CloverInteger(diff));
        } else {
            Object arguments[] = { date1, date2 };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "datediff - no Date expression");
        }

        return data;
    }

    public Object visit(CLVFMinusNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object value = stack.pop();

        if (value instanceof Numeric) {
            Numeric result = ((Numeric) value).duplicateNumeric();
            result.mul(Stack.NUM_MINUS_ONE);
            stack.push(result);
        } else {
            Object arguments[] = { value };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "minus - not a number");
        }

        return data;
    }

    public Object visit(CLVFReplaceNode node, Object data) {

        node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);
        node.jjtGetChild(2).jjtAccept(this, data);
        Object withO = stack.pop();
        Object regexO = stack.pop();
        Object str = stack.pop();

        if (withO == null || regexO == null || str == null) {
            throw new TransformLangExecutorRuntimeException(node, new Object[] { withO,
                    regexO, str }, "NULL value not allowed");
        }

        if (str instanceof CharSequence && withO instanceof CharSequence
                && regexO instanceof CharSequence) {

            if (node.pattern == null || !node.stored.equals(regexO)) {
                node.pattern = Pattern.compile(((CharSequence) regexO)
                        .toString());
                node.matcher = node.pattern.matcher((CharSequence) str);
                node.stored = regexO;
            } else {
                node.matcher.reset((CharSequence) str);
            }
            stack.push(node.matcher.replaceAll(((CharSequence) withO)
                    .toString()));

        } else {
            Object[] arguments = { withO, regexO, str };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "replace - wrong type of literal(s)");
        }

        return data;
    }

    public Object visit(CLVFNum2StrNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object a = stack.pop();

        if (a instanceof Numeric) {
            stack.push(((Numeric) a).toString());
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "num2str - wrong type of literal");
        }

        return data;
    }

    public Object visit(CLVFStr2NumNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object a = stack.pop();

        if (a instanceof CharSequence) {
            try {
                stack.push(new CloverDouble(Double
                        .parseDouble(((CharSequence) a).toString())));
            } catch (NumberFormatException ex) {
                Object[] arguments = { a };
                throw new TransformLangExecutorRuntimeException(arguments,
                        "str2num - can't convert \"" + a + "\"");
            }
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "str2num - wrong type of literal");
        }

        return data;
    }
    
    public Object visit(CLVFDate2StrNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object a = stack.pop();

        if (a instanceof Date) {
                stack.push(node.dateFormat.format((Date)a));
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "date2str - wrong type of literal");
        }

        return data;
    }
    
    public Object visit(CLVFStr2DateNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object a = stack.pop();

        if (a instanceof CharSequence) {
            try {
                stack.push(node.dateFormat.parse(((CharSequence)a).toString()));
            } catch (java.text.ParseException ex) {
                Object[] arguments = { a };
                throw new TransformLangExecutorRuntimeException(arguments,
                        "str2date - can't convert \"" + a + "\"");
            }
        } else {
            Object[] arguments = { a };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "str2date - wrong type of literal");
        }

        return data;
    }

    public Object visit(CLVFIffNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);

        Object condition = stack.pop();

        if (condition instanceof Boolean) {
            if (((Boolean) condition).booleanValue()) {
                node.jjtGetChild(1).jjtAccept(this, data);
            } else {
                node.jjtGetChild(2).jjtAccept(this, data);
            }
            stack.push(stack.pop());
        } else {
            Object[] arguments = { condition };
            throw new TransformLangExecutorRuntimeException(arguments,
                    "iif - wrong type of conditional expression");
        }

        return data;
    }

    public Object visit(CLVFPrintErrNode node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object a = stack.pop();

        System.err.println(a != null ? a : "<null>");

        // stack.push(Stack.TRUE_VAL);

        return data;
    }

    public Object visit(CLVFPrintStackNode node, Object data) {
        for (int i=stack.top;i>=0;i--){
            System.err.println("["+i+"] : "+stack.stack[i]);
        }
        

        return data;
    }

    
    /***************************************************************************
     * Transformation Language executor starts here.
     **************************************************************************/

    public Object visit(CLVFForStatement node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data); // set up of the loop
        boolean condition = false;
        Node loopCondition = node.jjtGetChild(1);
        Node increment = node.jjtGetChild(2);
        Node body = node.jjtGetChild(3);

        try {
            loopCondition.jjtAccept(this, data); // evaluate the condition
            condition = ((Boolean) stack.pop()).booleanValue();
        } catch (ClassCastException ex) {
            throw new TransformLangExecutorRuntimeException(node,"loop condition does not evaluate to BOOLEAN value");
        }

        // loop execution
        while (condition) {
            body.jjtAccept(this, data);
            stack.pop(); // in case there is anything on top of stack
            // check for break or continue statements
            if (breakFlag){ 
                breakFlag=false;
                if (breakType==BREAK_BREAK || breakType==BREAK_RETURN) return data;
            }
            increment.jjtAccept(this, data);
            stack.pop(); // in case there is anything on top of stack
            // evaluate the condition
            loopCondition.jjtAccept(this, data);
            try {
                condition = ((Boolean) stack.pop()).booleanValue();
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"loop condition does not evaluate to BOOLEAN value");
            }
        }

        return data;
    }

    public Object visit(CLVFWhileStatement node, Object data) {
        boolean condition = false;
        Node loopCondition = node.jjtGetChild(0);
        Node body = node.jjtGetChild(1);

        try {
            loopCondition.jjtAccept(this, data); // evaluate the condition
            condition = ((Boolean) stack.pop()).booleanValue();
        } catch (ClassCastException ex) {
            throw new TransformLangExecutorRuntimeException(node,"loop condition does not evaluate to BOOLEAN value");
        }

        // loop execution
        while (condition) {
            body.jjtAccept(this, data);
            stack.pop(); // in case there is anything on top of stack
            // check for break or continue statements
            if (breakFlag){ 
                breakFlag=false;
                if (breakType==BREAK_BREAK || breakType==BREAK_RETURN) return data;
            }
            // evaluate the condition
            loopCondition.jjtAccept(this, data);
            try {
                condition = ((Boolean) stack.pop()).booleanValue();
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"loop condition does not evaluate to BOOLEAN value");
            }
        }

        return data;
    }

    public Object visit(CLVFIfStatement node, Object data) {
        boolean condition = false;
        try {
            node.jjtGetChild(0).jjtAccept(this, data); // evaluate the
            // condition
            condition = ((Boolean) stack.pop()).booleanValue();
        } catch (ClassCastException ex) {
            throw new TransformLangExecutorRuntimeException(node,"condition does not evaluate to BOOLEAN value");
        }

        // first if
        if (condition) {
            node.jjtGetChild(1).jjtAccept(this, data);
            stack.pop(); // in case there is anything on top of stack
        } else { // if else part exists
            if (node.jjtGetNumChildren() > 2) {
                node.jjtGetChild(2).jjtAccept(this, data);
                stack.pop(); // in case there is anything on top of stack
            }
        }

        return data;
    }

    public Object visit(CLVFDoStatement node, Object data) {
        boolean condition = false;
        Node loopCondition = node.jjtGetChild(1);
        Node body = node.jjtGetChild(0);

        // loop execution
        do {
            body.jjtAccept(this, data);
            stack.pop(); // in case there is anything on top of stack
            // check for break or continue statements
            if (breakFlag){ 
                breakFlag=false;
                if (breakType==BREAK_BREAK || breakType==BREAK_RETURN) return data;
            }
            // evaluate the condition
            loopCondition.jjtAccept(this, data);
            try {
                condition = ((Boolean) stack.pop()).booleanValue();
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"loop condition does not evaluate to BOOLEAN value");
            }
        } while (condition);

        return data;
    }

    public Object visit(CLVFSwitchStatement node, Object data) {
        // get value of switch && push/leave it on stack
        node.jjtGetChild(0).jjtAccept(this, data);
        Object switchVal=stack.pop();
        int numChildren = node.jjtGetNumChildren();
        // loop over remaining case statements
        for (int i = 1; i < numChildren; i++) {
            stack.push(switchVal);
            node.jjtGetChild(i).jjtAccept(this, data);
            if (breakFlag) {
                if (breakType == BREAK_BREAK) {
                    breakFlag = false;
                }
                break;
            }
        }
        return data;
    }

    public Object visit(CLVFCaseExpression node, Object data) {
        // test if literal (as child 0) is equal to data on stack
        // if so, execute block (child 1)
        boolean match = false;
        Object switchVal = stack.pop();
        node.jjtGetChild(0).jjtAccept(this, data);
        Object value = stack.pop();
        try {
            if (switchVal instanceof Numeric) {
                match = (((Numeric) value).compareTo((Numeric) switchVal) == 0);
            } else if (switchVal instanceof CharSequence) {
                match = (Compare.compare((CharSequence) switchVal,
                        (CharSequence) value) == 0);
            } else if (switchVal instanceof Date) {
                match = (((Date) switchVal).compareTo((Date) value) == 0);
            } else if (switchVal instanceof Boolean) {
                match = ((Boolean) switchVal).equals((Boolean) value);
            }
        } catch (ClassCastException ex) {
            Object[] args=new Object[] {switchVal,value};
            throw new TransformLangExecutorRuntimeException(node,args,"incompatible literals in case clause");
        }
        if (match)
            node.jjtGetChild(1).jjtAccept(this, data);
        return data;
    }

    public Object visit(CLVFPlusPlusNode node, Object data) {
        Node childNode = node.jjtGetChild(0);
        if (childNode instanceof CLVFVariableLiteral) {
            try {
                CLVFVariableLiteral varNode=(CLVFVariableLiteral) childNode;
                Numeric num = (Numeric) stack.getVar(varNode.localVar, varNode.varSlot);
                num.add(Stack.NUM_ONE);
                stack.push(num.duplicateNumeric());
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"variable is not of numeric type");
            }
        } else {
            childNode.jjtAccept(this, data);
            try {
                Numeric num = ((Numeric) stack.pop()).duplicateNumeric();
                num.add(Stack.NUM_ONE);
                stack.push(num);
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"expression is not of numeric type");
            }
        }
        return data;
    }

    public Object visit(CLVFMinusMinusNode node, Object data) {
        Node childNode = node.jjtGetChild(0);
        if (childNode instanceof CLVFVariableLiteral) {
            try {
                CLVFVariableLiteral varNode=(CLVFVariableLiteral) childNode;
                Numeric num = (Numeric) stack.getVar(varNode.localVar, varNode.varSlot);
                num.sub(Stack.NUM_ONE);
                stack.push(num.duplicateNumeric());
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"variable is not of numeric type");
            }
        } else {
            childNode.jjtAccept(this, data);
            try {
                Numeric num = ((Numeric) stack.pop()).duplicateNumeric();
                num.add(Stack.NUM_ONE);
                stack.push(num);
            } catch (ClassCastException ex) {
                throw new TransformLangExecutorRuntimeException(node,"expression is not of numeric type");
            }
        }
        return data;
    }

    public Object visit(CLVFBlock node, Object data) {
        int childern = node.jjtGetNumChildren();
        for (int i = 0; i < childern; i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
            stack.pop(); // in case there is anything on top of stack
            // have we seen contiue/break statement ??
            if (breakFlag){ 
                return data;
            }
        }
        return data;
    }

    /*
     * Loop & block & function control nodes
     */

    public Object visit(CLVFBreakStatement node, Object data) {
        breakFlag = true; // we encountered break statement;
        breakType=BREAK_BREAK;
        return data;
    }

    public Object visit(CLVFContinueStatement node, Object data) {
        breakFlag = true; // we encountered continue statement;
        breakType= BREAK_CONTINUE;
        return data;
    }

    public Object visit(CLVFReturnStatement node, Object data) {
        if (node.jjtHasChildren()){
            node.jjtGetChild(0).jjtAccept(this, data);
        }
        breakFlag = true;
        breakType = BREAK_RETURN;
        return data;
    }

    public Object visit(CLVFBreakpointNode node, Object data) {
        // TODO
        return data;
    }
    
    /*
     * Variable declarations
     */
    public Object visit(CLVFVarDeclaration node, Object data) {
        // test for duplicite declaration - should have been done before
        /*if (stack.symtab.containsKey(node.name)) {
            throw new TransformLangExecutorRuntimeException(node,
                    "variable already declared - \"" + node.name + "\"");
        }*/
        Object value;
        // create global/local variable
        switch (node.type) {
        case INT_VAR:
            value= new CloverInteger(0);
            break;
        case LONG_VAR:
            value= new CloverLong(0);
            break;
        case DOUBLE_VAR:
            value= new CloverDouble(0);
        case DECIMAL_VAR:
            value= DecimalFactory.getDecimal();
            break;
        case STRING_VAR:
            value= new StringBuffer();
            break;
        case DATE_VAR:
            value=new Date();
            break;
        case BOOL_VAR:
            value=  Stack.FALSE_VAL;
            break;
        default:
            throw new TransformLangExecutorRuntimeException(node,
                    "variable declaration - "
                            + "unknown variable type for variable \""
                            + node.name + "\"");

        }
        stack.storeVar(node.localVar, node.varSlot, value);
        
        return data;
    }

    public Object visit(CLVFVariableLiteral node, Object data) {
        Object var = stack.getVar(node.localVar, node.varSlot);
        // variable can be null
            stack.push(var);
        /*
        if (var != null) {
            stack.push(var);
        } else {
            throw new TransformLangExecutorRuntimeException(node, "unknown variable \""
                    + node.varName + "\"");
        }
        */
        return data;
    }

    public Object visit(CLVFAssignment node, Object data) {
        CLVFVariableLiteral childNode=(CLVFVariableLiteral) node.jjtGetChild(0);

        Object variable = stack.getVar(childNode.localVar,childNode.varSlot);
        node.jjtGetChild(1).jjtAccept(this, data);
        Object value = stack.pop();
        try {
            if (variable instanceof Numeric) {
                    ((Numeric) variable).setValue((Numeric) value);
            } else if (variable instanceof StringBuffer) {
                StringBuffer var = (StringBuffer) variable;
                var.setLength(0);
                CharSequence seqA = (CharSequence) value;
                var.ensureCapacity(seqA.length());
                for (int j = 0; j < seqA.length(); j++) {
                    var.append(seqA.charAt(j));
                }
            } else if (variable instanceof Boolean) {
                stack.storeVar(childNode.localVar,childNode.varSlot, (Boolean)value); // boolean is not updatable - we replace the reference
                // stack.put(varName,((Boolean)value).booleanValue() ?
                // Stack.TRUE_VAL : Stack.FALSE_VAL);
            } else if (variable instanceof Date) {
                ((Date) variable).setTime(((Date) value).getTime());
            } else {
                throw new TransformLangExecutorRuntimeException(node,
                        "unknown variable \"" + childNode.varName + "\"");
            }
        } catch (ClassCastException ex) {
            throw new TransformLangExecutorRuntimeException(node,
                    "invalid assignment of \"" + value + "\" to variable \""
                            + childNode.varName + "\"");
        }

        return data;
    }

    
    public Object visit(CLVFMapping node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        Object value=stack.pop();
        DataField field=outputRecords[node.recordNo].getField(node.fieldNo);
        try{
            //TODO: small hack
            if (field instanceof Numeric){
                    ((Numeric)field).setValue((Numeric)value);
            }else{
                field.setValue(value);
            }
            //outputRecords[node.recordNo].getField(node.fieldNo).setValue(value);
        }catch(BadDataFormatException ex){
            if (!outputRecords[node.recordNo].getField(node.fieldNo).getMetadata().isNullable()){
                throw new TransformLangExecutorRuntimeException(node,"can't assign NULL to \"" + node.fieldName + "\"");
            }else{
                throw new TransformLangExecutorRuntimeException(node,"data format exception when mapping \"" + node.fieldName + "\" - assigning \""
                            + value + "\"");
            }
        }catch(Exception ex){
            String msg=ex.getMessage();
            throw new TransformLangExecutorRuntimeException(node,
                    (msg!=null ? msg : "") +
                    " when mapping \"" + node.fieldName + "\" ("+DataFieldMetadata.type2Str(field.getType())
                    +") - assigning \"" + value + "\" ("+value.getClass()+")");
        }
        
        return data;
    }
    
    
    
    /*
     * Declaration & calling of Functions here
     */
    public Object visit(CLVFFunctionCallStatement node, Object data) {
        //put call parameters on stack
        node.childrenAccept(this,data);
        CLVFFunctionDeclaration executionNode=node.callNode;
        // open call frame
        stack.pushFuncCallFrame();
        // store call parameters from stack as local variables
        for (int i=executionNode.numParams-1;i>=0; stack.storeLocalVar(i--,stack.pop()));
       
        // execute function body
        // loop execution
        Object returnData;
        int numChildren=executionNode.jjtGetNumChildren();
        for (int i=0;i<numChildren;i++){
            executionNode.jjtGetChild(i).jjtAccept(this,data);
            returnData=stack.pop(); // in case there is anything on top of stack
            // check for break or continue statements
            if (breakFlag){ 
                breakFlag=false;
                if (breakType==BREAK_RETURN){
                    if (returnData!=null)
                        stack.push(returnData);
                    break;
                }
            }
        }
        stack.popFuncCallFrame();
        return data;
    }

    public Object visit(CLVFFunctionDeclaration node, Object data) {
        return data;
    }

    public Object visit(CLVFStatementExpression node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        return data;
    }

    
}