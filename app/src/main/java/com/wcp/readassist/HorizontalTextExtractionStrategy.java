package com.wcp.readassist;

import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.Matrix;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;

import java.lang.reflect.Field;
import java.util.List;

public class HorizontalTextExtractionStrategy extends LocationTextExtractionStrategy {
    public class HorizontalTextChunk extends TextChunk
    {
        public HorizontalTextChunk(String string, Vector startLocation, Vector endLocation, float charSpaceWidth)
        {
            super(string, startLocation, endLocation, charSpaceWidth);
        }

        @Override
        public int compareTo(TextChunk rhs)
        {
            if (rhs instanceof HorizontalTextChunk)
            {
                HorizontalTextChunk horRhs = (HorizontalTextChunk) rhs;
                int rslt = Integer.compare(getLineNumber(), horRhs.getLineNumber());
                if (rslt != 0) return rslt;
                return Float.compare(getStartLocation().get(Vector.I1), rhs.getStartLocation().get(Vector.I1));
            }
            else
                return super.compareTo(rhs);
        }

//        @Override
//        public boolean sameLine(TextChunk as)
//        {
//            if (as instanceof HorizontalTextChunk)
//            {
//                HorizontalTextChunk horAs = (HorizontalTextChunk) as;
//                return getLineNumber() == horAs.getLineNumber();
//            }
//            else
//                return super.sameLine(as);
//        }

        public int getLineNumber()
        {
            Vector startLocation = getStartLocation();
            float y = startLocation.get(Vector.I2);
            List<Float> flips = textLineFinder.verticalFlips;
            if (flips == null || flips.isEmpty())
                return 0;
            if (y < flips.get(0))
                return flips.size() / 2 + 1;
            for (int i = 1; i < flips.size(); i+=2)
            {
                if (y < flips.get(i))
                {
                    return (1 + flips.size() - i) / 2;
                }
            }
            return 0;
        }
    }

    @Override
    public void renderText(TextRenderInfo renderInfo)
    {
        textLineFinder.renderText(renderInfo);

        LineSegment segment = renderInfo.getBaseline();
        if (renderInfo.getRise() != 0){ // remove the rise from the baseline - we do this because the text from a super/subscript render operations should probably be considered as part of the baseline of the text the super/sub is relative to
            Matrix riseOffsetTransform = new Matrix(0, -renderInfo.getRise());
            segment = segment.transformBy(riseOffsetTransform);
        }
        TextChunk location = new HorizontalTextChunk(renderInfo.getText(), segment.getStartPoint(), segment.getEndPoint(), renderInfo.getSingleSpaceWidth());
        getLocationalResult().add(location);
    }

    public HorizontalTextExtractionStrategy() throws NoSuchFieldException, SecurityException
    {
        locationalResultField = LocationTextExtractionStrategy.class.getDeclaredField("locationalResult");
        locationalResultField.setAccessible(true);

        textLineFinder = new TextLineFinder();
    }

    @SuppressWarnings("unchecked")
    List<TextChunk> getLocationalResult()
    {
        try
        {
            return (List<TextChunk>) locationalResultField.get(this);
        }
        catch (IllegalArgumentException | IllegalAccessException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    final Field locationalResultField;
    final TextLineFinder textLineFinder;
}
