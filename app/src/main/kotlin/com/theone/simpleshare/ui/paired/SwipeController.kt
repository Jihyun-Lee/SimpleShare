package com.theone.simpleshare.ui.paired

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/*
 *  Referenced from https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
 */
internal enum class ButtonsState {
    GONE, LEFT_VISIBLE, RIGHT_VISIBLE
}

internal class SwipeController(buttonsActions: SwipeControllerActions?) :
    ItemTouchHelper.Callback() {
    private var swipeBack = false
    private var buttonShowedState = ButtonsState.GONE
    private lateinit var buttonInstance: RectF
    private var currentItemViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var buttonsActions: SwipeControllerActions
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
    }

     override fun onMove(
         recyclerView: RecyclerView,
         viewHolder: RecyclerView.ViewHolder,
         target: RecyclerView.ViewHolder
     ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    override fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        if (swipeBack) {
            swipeBack = buttonShowedState != ButtonsState.GONE
            return 0
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        var dX = dX
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (buttonShowedState != ButtonsState.GONE) {
                if (buttonShowedState == ButtonsState.LEFT_VISIBLE) dX = Math.max(dX, buttonWidth)
                if (buttonShowedState == ButtonsState.RIGHT_VISIBLE) dX = Math.min(dX, -buttonWidth)
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            } else {
                setTouchListener(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }
        if (buttonShowedState == ButtonsState.GONE) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
        currentItemViewHolder = viewHolder
    }

    private fun setTouchListener(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        recyclerView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                swipeBack =
                    event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP
                if (swipeBack) {
                    if (dX < -buttonWidth) {
                        buttonShowedState = ButtonsState.RIGHT_VISIBLE
                    } else if (dX > buttonWidth) {
                        buttonShowedState = ButtonsState.LEFT_VISIBLE
                    }
                    if (buttonShowedState != ButtonsState.GONE) {
                        setTouchDownListener(
                            c,
                            recyclerView,
                            viewHolder,
                            dX,
                            dY,
                            actionState,
                            isCurrentlyActive
                        )
                        setItemsClickable(recyclerView, false)
                    }
                }
                return false
            }
        })
    }

    private fun setTouchDownListener(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        recyclerView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    setTouchUpListener(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
                return false
            }
        })
    }

    private fun setTouchUpListener(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        recyclerView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    super@SwipeController.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        0f,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                    recyclerView.setOnTouchListener(object : View.OnTouchListener {
                        override fun onTouch(v: View, event: MotionEvent): Boolean {
                            return false
                        }
                    })
                    setItemsClickable(recyclerView, true)
                    swipeBack = false
                    if (buttonsActions != null && buttonInstance != null && buttonInstance.contains(
                            event.x,
                            event.y
                        )
                    ) {
                        if (buttonShowedState == ButtonsState.LEFT_VISIBLE) {
                            buttonsActions.onLeftClicked(viewHolder.adapterPosition)
                        } else if (buttonShowedState == ButtonsState.RIGHT_VISIBLE) {
                            buttonsActions.onRightClicked(viewHolder.adapterPosition)
                        }
                    }
                    buttonShowedState = ButtonsState.GONE
                }
                return false
            }
        })
    }

    private fun setItemsClickable(recyclerView: RecyclerView, isClickable: Boolean) {
        for (i in 0 until recyclerView.childCount) {
            recyclerView.getChildAt(i).isClickable = isClickable
        }
    }

    private fun drawButtons(c: Canvas, viewHolder: RecyclerView.ViewHolder) {
        val buttonWidthWithoutPadding = buttonWidth - 20
        val corners = 16f
        val itemView: View = viewHolder.itemView
        val p = Paint()

        with(itemView) {
            if (buttonShowedState == ButtonsState.LEFT_VISIBLE) {
                val leftButton = RectF(
                    left.toFloat(), top.toFloat(),
                    left + buttonWidthWithoutPadding, bottom.toFloat()
                )
                p.color = Color.BLUE
                c.drawRoundRect(leftButton, corners, corners, p)
                drawText("EDIT", c, leftButton, p)
                buttonInstance = leftButton
            } else if (buttonShowedState == ButtonsState.RIGHT_VISIBLE) {
                val rightButton = RectF(
                    right - buttonWidthWithoutPadding, top.toFloat(),
                    right.toFloat(), bottom.toFloat()
                )
                p.color = Color.RED
                c.drawRoundRect(rightButton, corners, corners, p)
                drawText("DELETE", c, rightButton, p)
                buttonInstance = rightButton
            }
        }
    }

    private fun drawText(text: String, c: Canvas, button: RectF, p: Paint) {

        with(p){
            color = Color.WHITE
            isAntiAlias = true
            textSize = textSize
        }
        val textSize = 30f
        val textWidth = p.measureText(text)
        c.drawText(text, button.centerX() - textWidth / 2, button.centerY() + textSize / 2, p)
    }

    fun onDraw(c: Canvas) {
        currentItemViewHolder?.let { drawButtons(c, it) }
    }

    companion object {
        private const val TAG = "SwipeController"
        private const val buttonWidth = 200f
    }

    init {
        if (buttonsActions != null) {
            this.buttonsActions = buttonsActions
        }
    }
}