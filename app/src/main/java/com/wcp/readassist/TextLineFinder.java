package com.wcp.readassist;

import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextLineFinder implements RenderListener
{
    @Override
    public void beginTextBlock() { }
    @Override
    public void endTextBlock() { }

    @Override
    public void renderImage(ImageRenderInfo renderInfo) { }

    /*
     * @see RenderListener#renderText(TextRenderInfo)
     */
    @Override
    public void renderText(TextRenderInfo renderInfo)
    {
        LineSegment ascentLine = renderInfo.getAscentLine();
        LineSegment descentLine = renderInfo.getDescentLine();
        float[] yCoords = new float[]{
                ascentLine.getStartPoint().get(Vector.I2),
                ascentLine.getEndPoint().get(Vector.I2),
                descentLine.getStartPoint().get(Vector.I2),
                descentLine.getEndPoint().get(Vector.I2)
        };
        Arrays.sort(yCoords);
        addVerticalUseSection(yCoords[0], yCoords[3]);
    }

    /**
     * This method marks the given interval as used.
     */
    void addVerticalUseSection(float from, float to)
    {
        if (to < from)
        {
            float temp = to;
            to = from;
            from = temp;
        }

        int i=0, j=0;
        for (; i<verticalFlips.size(); i++)
        {
            float flip = verticalFlips.get(i);
            if (flip < from)
                continue;

            for (j=i; j<verticalFlips.size(); j++)
            {
                flip = verticalFlips.get(j);
                if (flip < to)
                    continue;
                break;
            }
            break;
        }
        boolean fromOutsideInterval = i%2==0;
        boolean toOutsideInterval = j%2==0;

        while (j-- > i)
            verticalFlips.remove(j);
        if (toOutsideInterval)
            verticalFlips.add(i, to);
        if (fromOutsideInterval)
            verticalFlips.add(i, from);
    }

    final List<Float> verticalFlips = new ArrayList<Float>();
}
