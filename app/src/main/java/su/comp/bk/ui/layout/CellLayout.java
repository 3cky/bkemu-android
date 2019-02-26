package su.comp.bk.ui.layout;

import su.comp.bk.R;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import androidx.core.view.GravityCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;


/**
 * A layout manager that allows splitting parents area into evenly sized cells
 * grid. Each child can be positioned across one or several cells.
 *
 * @author sky
 */
public class CellLayout extends ViewGroup {
    private static final int DEFAULT_CHILD_GRAVITY = Gravity.TOP | Gravity.CENTER;

    /**
     * Default size in dp that will be used for a cell in case no other clues
     * were given by parent.
     */
    private static final int DEFAULT_CELL_SIZE = 32;

    private static final boolean DEBUG = false;

    /**
     * Number of columns.
     */
    private int columnCount = 1;
    /**
     * Number of rows (0 - auto detecting rows count).
     */
    private int rowCount = 0;

    /**
     * An optional margin to be applied to each child.
     */
    private int spacing = 0;

    private float cellSize;
    private float cellSize2;

    private int rows;

    /** Whether to clip children with dimension=WRAP_CONTENT to cell size or not. */
    private boolean clipChildrenToCellSize;

    private static final int WRAP_CONTENT_MODE_NONE = 0;
    private static final int WRAP_CONTENT_MODE_MEASURE = 1;

    private int wrapWidthMode;
    private int wrapHeightMode;

    private static final int CELL_SIZE_MODE_DEFAULT = 0;
    private static final int CELL_SIZE_MODE_EQUAL_BY_WIDTH = 1;
    private static final int CELL_SIZE_MODE_EQUAL_BY_HEIGHT = 2;

    private int cellSizeMode;

    public CellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        initAttrs(context, attrs);
    }

    public CellLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        initAttrs(context, attrs);
    }

    public CellLayout(Context context) {
        super(context);
    }

    public void initAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CellLayout, 0, 0);

        try {
            columnCount = a.getInt(R.styleable.CellLayout_columnCount, 1);
            rowCount = a.getInt(R.styleable.CellLayout_rowCount, 0);
            spacing = a.getDimensionPixelSize(R.styleable.CellLayout_spacing, 0);
            clipChildrenToCellSize = a.getBoolean(R.styleable.CellLayout_clipToCellSize, false);
            wrapWidthMode = a.getInteger(R.styleable.CellLayout_wrapWidthMode, WRAP_CONTENT_MODE_NONE);
            wrapHeightMode = a.getInteger(R.styleable.CellLayout_wrapHeightMode, WRAP_CONTENT_MODE_NONE);
            cellSizeMode = a.getInteger(R.styleable.CellLayout_cellSizeMode, CELL_SIZE_MODE_DEFAULT);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void requestLayout() {
        suggestedCellWidth = suggestedCellHeight = Float.NaN;
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int width = 0;
        int height = 0;

        int horPadding = getPaddingLeft() + getPaddingRight();
        int verPadding = getPaddingTop() + getPaddingBottom();

        boolean isCellSizeModeByWidth = cellSizeMode == CELL_SIZE_MODE_EQUAL_BY_WIDTH;
        boolean isCellSizeModeByHeight = cellSizeMode == CELL_SIZE_MODE_EQUAL_BY_HEIGHT;

        int doubleSpacing = spacing * 2;

        if (isCellSizeModeByWidth || cellSizeMode == CELL_SIZE_MODE_DEFAULT) {
            if (widthMode == MeasureSpec.EXACTLY) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            } else {
                ViewGroup.LayoutParams lp = getLayoutParams();
                if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    width = getSuggestedMinimumWidth();
                    if (wrapWidthMode == WRAP_CONTENT_MODE_MEASURE) {
                        calculateSuggestedCellSize();
                        width = Math.max(width, (int) Math.ceil(suggestedCellWidth * columnCount + horPadding));
                    }
                }
                if (widthMode == MeasureSpec.UNSPECIFIED) {
                    if (width == 0) {
                        float defCellSize = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, DEFAULT_CELL_SIZE,
                                getResources().getDisplayMetrics());
                        width = (int) (columnCount * defCellSize + horPadding);
                    }
                } else { // AT_MOST
                    if (width == 0) {
                        width = MeasureSpec.getSize(widthMeasureSpec);
                    } else {
                        width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                    }
                }
            }
            cellSize = (float) (width - horPadding) / (float) columnCount;
        }

        if (isCellSizeModeByHeight || (cellSizeMode == CELL_SIZE_MODE_DEFAULT && rowCount > 0)) {
            if (heightMode == MeasureSpec.EXACTLY) {
                height = MeasureSpec.getSize(heightMeasureSpec);
            } else {
                ViewGroup.LayoutParams lp = getLayoutParams();
                if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    calculateSuggestedCellSize();
                    height = getSuggestedMinimumHeight();
                    if (wrapHeightMode == WRAP_CONTENT_MODE_MEASURE) {
                        calculateSuggestedCellSize();
                        height = Math.max(height, (int) Math.ceil(suggestedCellHeight * rowCount + verPadding));
                    }
                }
                if (heightMode == MeasureSpec.UNSPECIFIED) {
                    if (height == 0) {
                        float defCellSize = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, DEFAULT_CELL_SIZE,
                                getResources().getDisplayMetrics());
                        height = (int) (rowCount * defCellSize + verPadding);
                    }
                } else { // AT_MOST
                    if (height == 0) {
                        height = MeasureSpec.getSize(heightMeasureSpec);
                    } else {
                        height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                    }
                }
            }
            cellSize2 = (float) (height - verPadding) / (float) rowCount;
        } else {
            cellSize2 = cellSize;
        }

        if (isCellSizeModeByWidth) {
            cellSize2 = cellSize;
        } else if (isCellSizeModeByHeight) {
            cellSize = cellSize2;
        }

        int childCount = getChildCount();
        View child;

        rows = 0;

        for (int i = 0; i < childCount; i++) {
            child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int top = lp.row;
            int w = lp.columnSpan;
            int h = lp.rowSpan;

            int bottom = top + h;

            // Measuring only children that is not gone. But children with gone visibility is
            // taken into account during calculating the amount of rows.
            if (child.getVisibility() != GONE) {

                int childWidthSpec = getChildMeasureSpecInternal(widthMeasureSpec,
                             (int) (w * cellSize) - doubleSpacing - lp.leftMargin - lp.rightMargin,
                             lp.width, lp.gravity != -1 && (lp.gravity & Gravity.FILL_HORIZONTAL) == Gravity.FILL_HORIZONTAL);
                int childHeightSpec = getChildMeasureSpecInternal(heightMeasureSpec,
                             (int) (h * cellSize2) - doubleSpacing - lp.topMargin - lp.bottomMargin,
                             lp.height, lp.gravity != -1 && (lp.gravity & Gravity.FILL_VERTICAL) == Gravity.FILL_VERTICAL);

                child.measure(childWidthSpec, childHeightSpec);

            }

            if (bottom > rows) {
                rows = bottom;
            }
        }

        if (rowCount > 0) {
            rows = rowCount;
        }

        int measuredHeight = Math.round(rows * cellSize2) + verPadding;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else if (heightMode == MeasureSpec.AT_MOST) {
            int atMostHeight = MeasureSpec.getSize(heightMeasureSpec);
            height = Math.min(atMostHeight, measuredHeight);
        } else {
            height = measuredHeight;
        }

        int measuredWidth = Math.round(columnCount * cellSize) + horPadding;
        if (widthMode == MeasureSpec.EXACTLY) {
            width = MeasureSpec.getSize(widthMeasureSpec);
        } else if (widthMode == MeasureSpec.AT_MOST) {
            int atMostWidth = MeasureSpec.getSize(widthMeasureSpec);
            width = Math.min(atMostWidth, measuredWidth);
        } else {
            width = measuredWidth;
        }

        setMeasuredDimension(width, height);
    }


    private int getChildMeasureSpecInternal(int spec, int childCellsSize, int childDimension, boolean isFillGravity) {
        int resultSize = 0;
        int resultMode = MeasureSpec.UNSPECIFIED;

        if (isFillGravity) {
            // Child wants to be our size. So be it.
            resultSize = childCellsSize;
            resultMode = MeasureSpec.EXACTLY;
        } else {
            int specMode = MeasureSpec.getMode(spec);
            switch (specMode) {
                // Parent has imposed an exact size on us
                case MeasureSpec.EXACTLY:
                    if (childDimension >= 0) {
                        resultSize = childDimension;
                        resultMode = MeasureSpec.EXACTLY;
                    } else if (childDimension == ViewGroup.LayoutParams.MATCH_PARENT) {
                        // Child wants to be our size. So be it.
                        resultSize = childCellsSize;
                        resultMode = MeasureSpec.EXACTLY;
                    } else if (childDimension == ViewGroup.LayoutParams.WRAP_CONTENT) {
                        // Child wants to determine its own size. It can't be
                        // bigger than us.
                        if (clipChildrenToCellSize) {
                            resultSize = childCellsSize;
                            resultMode = MeasureSpec.AT_MOST;
                        }
                    }
                    break;

                // Parent has imposed a maximum size on us
                case MeasureSpec.AT_MOST:
                    if (childDimension >= 0) {
                        // Child wants a specific size... so be it
                        resultSize = childDimension;
                        resultMode = MeasureSpec.EXACTLY;
                    } else if (childDimension == ViewGroup.LayoutParams.MATCH_PARENT) {
                        // Child wants to be our size, but our size is not fixed.
                        // Constrain child to not be bigger than us.
                        resultSize = childCellsSize;
                        resultMode = MeasureSpec.EXACTLY;
                    } else if (childDimension == ViewGroup.LayoutParams.WRAP_CONTENT) {
                        // Child wants to determine its own size. It can't be
                        // bigger than us.
                        if (clipChildrenToCellSize) {
                            resultSize = childCellsSize;
                            resultMode = MeasureSpec.AT_MOST;
                        }
                    }
                    break;

                // Parent asked to see how big we want to be
                case MeasureSpec.UNSPECIFIED:
                    if (childDimension >= 0) {
                        // Child wants a specific size... let him have it
                        resultSize = childDimension;
                        resultMode = MeasureSpec.EXACTLY;
                    } else if (childDimension == ViewGroup.LayoutParams.MATCH_PARENT) {
                        // Child wants to be our size... find out how big it should
                        // be
                        resultSize = childCellsSize;
                        resultMode = MeasureSpec.EXACTLY;
                    } else if (childDimension == ViewGroup.LayoutParams.WRAP_CONTENT) {
                        // Child wants to determine its own size.... find out how
                        // big it should be
                        if (clipChildrenToCellSize) {
                            resultSize = childCellsSize;
                            resultMode = MeasureSpec.AT_MOST;
                        }
                    }
                    break;
            }
        }

        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();

        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        View child;
        for (int i = 0; i < childCount; i++) {
            child = getChildAt(i);

            // laying out children that is not gone
            if (child.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int cellLeft = (int) (paddingLeft + lp.column * cellSize + spacing);
                int cellTop = (int) (paddingTop + lp.row * cellSize2 + spacing);
                float cellRight = (lp.column + lp.columnSpan) * cellSize + paddingLeft - spacing;
                float cellBottom = (lp.row + lp.rowSpan) * cellSize2 + paddingTop - spacing;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                final int layoutDirection = ViewCompat.getLayoutDirection(this);
                final int absoluteGravity = GravityCompat.getAbsoluteGravity(gravity, layoutDirection);
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.START:
                        childLeft = cellLeft + lp.leftMargin;
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (int) (cellLeft + (cellRight - cellLeft - width) / 2
                                + lp.leftMargin - lp.rightMargin);
                        break;
                    case Gravity.END:
                        childLeft = (int) (cellRight - width - lp.rightMargin);
                        break;
                    default:
                        childLeft = cellLeft + lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = cellTop + lp.topMargin;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = (int) (cellTop + (cellBottom - cellTop - height) / 2
                                + lp.topMargin - lp.bottomMargin);
                        break;
                    case Gravity.BOTTOM:
                        childTop = (int) (cellBottom - height - lp.bottomMargin);
                        break;
                    default:
                        childTop = cellTop + lp.topMargin;
                }

                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new CellLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof CellLayout.LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new CellLayout.LayoutParams(p);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return clipChildrenToCellSize ? new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         ViewGroup.LayoutParams.MATCH_PARENT)
                                      : new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                         ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static class LayoutParams extends FrameLayout.LayoutParams {

        /**
         * An X coordinate of the left most cell the view resides in.
         */
        public int column = 0;

        /**
         * An Y coordinate of the top most cell the view resides in.
         */
        public int row = 0;

        /**
         * Number of cells occupied by the view horizontally.
         */
        public int columnSpan = 1;

        /**
         * Number of cells occupied by the view vertically.
         */
        public int rowSpan = 1;

        /**
         * Whether to take into account the width of this child when the cell size is determined
         * by the child dimension or not.
         */
        public boolean meantWidth = true;

        /**
         * Whether to take into account the height of this child when the cell size is determined
         * by the child dimension or not.
         */
        public boolean meantHeight = true;

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);

            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CellLayout);
            try {
                column = a.getInt(R.styleable.CellLayout_layout_column, 0);
                row = a.getInt(R.styleable.CellLayout_layout_row, 0);
                columnSpan = a.getInt(R.styleable.CellLayout_layout_columnSpan, 1);
                rowSpan = a.getInt(R.styleable.CellLayout_layout_rowSpan, 1);
                meantWidth = a.getBoolean(R.styleable.CellLayout_layout_meantWidth, true);
                meantHeight = a.getBoolean(R.styleable.CellLayout_layout_meantHeight, true);
            } finally {
                a.recycle();
            }
        }

        public LayoutParams(ViewGroup.LayoutParams params) {
            super(params);

            if (params instanceof LayoutParams) {
                LayoutParams cellLayoutParams = (LayoutParams) params;
                column = cellLayoutParams.column;
                row = cellLayoutParams.row;
                rowSpan = cellLayoutParams.rowSpan;
                columnSpan = cellLayoutParams.columnSpan;
                meantWidth = cellLayoutParams.meantWidth;
                meantHeight = cellLayoutParams.meantHeight;
            }
        }

        public LayoutParams() {
            this(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }

    Paint debugPaint;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (DEBUG) {
            int l = 0;
            int t = 0;
            int r = getWidth();
            int b = getHeight();

            int cellsLeft = getPaddingLeft();
            int cellsTop = getPaddingTop();
            int cellsRight = r - getPaddingRight();
            int cellsBottom = b - getPaddingBottom();

            if (debugPaint == null) {
                debugPaint = new Paint();
                debugPaint.setStyle(Style.STROKE);
            }

            // drawing paddings
            debugPaint.setColor(0xFFFFFF00);
            canvas.drawRect(cellsLeft, cellsTop, cellsRight, cellsBottom, debugPaint);

            // drawing our bounds
            debugPaint.setColor(0xFF0000FF);
            canvas.drawRect(l, t, r, b, debugPaint);

            // drawing cells grid
            for (int col = 1; col < columnCount; ++col) {
                float x = col * cellSize + cellsLeft;
                canvas.drawLine(x, cellsTop, x, cellsBottom, debugPaint);
            }
            for (int row = 1; row <= rows; ++row) {
                float y = row * cellSize2 + cellsTop;
                canvas.drawLine(cellsLeft, y, cellsRight, y, debugPaint);
            }

            // drawing middle lines of the layout
            debugPaint.setColor(0xFF00FFFF);
            int middleX = (cellsRight + cellsLeft) / 2;
            int middleY = (cellsBottom + cellsTop) / 2;
            canvas.drawLine(middleX, cellsTop, middleX, cellsBottom, debugPaint);
            canvas.drawLine(cellsLeft, middleY, cellsRight, middleY, debugPaint);

            // drawing children bounds
            debugPaint.setColor(0xFFFF00FF);
            for (int n = 0; n < getChildCount(); ++n) {
                View child = getChildAt(n);
                if (child.getVisibility() != GONE) {
                    canvas.drawRect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), debugPaint);
                }
            }
        }
        super.dispatchDraw(canvas);
    }

    /**
     * @return the clipChildrenToCells
     */
    public boolean isClipChildrenToCells() {
        return clipChildrenToCellSize;
    }

    /**
     * @param clipChildrenToCells the clipChildrenToCells to set
     */
    public void setClipChildrenToCells(boolean clipChildrenToCells) {
        this.clipChildrenToCellSize = clipChildrenToCells;
    }

    float suggestedCellWidth = Float.NaN;
    float suggestedCellHeight = Float.NaN;

    @SuppressWarnings("static-access")
    void calculateSuggestedCellSize() {
        if (Float.isNaN(suggestedCellWidth)) {
            int doubleSpacing = spacing * 2;
            float calcCellWidth = 0;
            float calcCellHeight = 0;
            int childCount = getChildCount();
            for (int n = 0; n < childCount; ++n) {
                View child = getChildAt(n);
                if (child.getVisibility() != GONE) {
                    LayoutParams childLp = (LayoutParams) child.getLayoutParams();
                    boolean isMeantWidth = childLp.width != LayoutParams.MATCH_PARENT && childLp.meantWidth;
                    boolean isMeantHeight = childLp.height != LayoutParams.MATCH_PARENT && childLp.meantHeight;
                    if (isMeantWidth || isMeantHeight) {
//                        child.measure(UNSPECIFIED_MS, UNSPECIFIED_MS);
                        child.measure(getScrapChildMeasureSpec(childLp.width),
                                      getScrapChildMeasureSpec(childLp.height));
                        if (isMeantWidth) {
                            float spreadCellWidth =
                                    ((float) child.getMeasuredWidth() + doubleSpacing) / childLp.columnSpan;
                            if (calcCellWidth < spreadCellWidth) {
                                calcCellWidth = spreadCellWidth;
                            }
                        }
                        if (isMeantHeight) {
                            float spreadCellHeight =
                                    ((float) child.getMeasuredHeight() + doubleSpacing) / childLp.rowSpan;
                            if (calcCellHeight < spreadCellHeight) {
                                calcCellHeight = spreadCellHeight;
                            }
                        }
                    }
                }
            }
            suggestedCellWidth = calcCellWidth;
            suggestedCellHeight = calcCellHeight;
        }
    }

    private int getScrapChildMeasureSpec(int childDimension) {
        int resultSize = 0;
        int resultMode = 0;

        // Parent asked to see how big we want to be
        if (childDimension >= 0) {
            // Child wants a specific size... let him have it
            resultSize = childDimension;
            resultMode = MeasureSpec.EXACTLY;
        }

        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }
}
