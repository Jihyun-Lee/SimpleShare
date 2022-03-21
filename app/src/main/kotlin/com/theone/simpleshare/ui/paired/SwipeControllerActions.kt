package com.theone.simpleshare.ui.paired

/*
 *  Referenced from https://codeburst.io/android-swipe-menu-with-recyclerview-8f28a235ff28
 */
abstract class SwipeControllerActions {
    open fun onLeftClicked(position: Int) {}
    open fun onRightClicked(position: Int) {}
}