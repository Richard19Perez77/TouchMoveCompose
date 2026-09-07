package com.rick.touchmovecompose

import android.graphics.Point

/**
 * Pixel path from [a] to [b] used when a cube starts moving.
 *
 * Dispatches to eight helpers (cardinal + diagonal). Diagonals fill the longer
 * axis one pixel at a time and interpolate the shorter axis — not Bresenham,
 * so the original flight feel is preserved.
 */
class PlotPoints {
    var a: Point? = null
    var b: Point? = null
    var point: Point? = null
    lateinit var pointArr: Array<Point?>

    /** Fill [pointArr] with the pixel steps from [pointA] to [pointB]. */
    fun plotLine(pointA: Point?, pointB: Point?): Array<Point?> {
        a = pointA
        b = pointB
        PlotPointsToLocation()
        return pointArr
    }

    /** Pick the octant from [a] to [b] and fill [pointArr]. */
    fun PlotPointsToLocation() {
        if (a!!.y > b!!.y) {
            if (a!!.x < b!!.x) {
                plotPointsUpRight()
            } else if (a!!.x > b!!.x) {
                plotPointsUpLeft()
            } else {
                plotPointsUp()
            }
        } else if (a!!.y < b!!.y) {
            if (a!!.x < b!!.x) {
                plotPointsDownRight()
            } else if (a!!.x > b!!.x) {
                plotPointsDownLeft()
            } else {
                plotPointsDown()
            }
        } else if (a!!.x > b!!.x) {
            plotPointsLeft()
        } else {
            plotPointsRight()
        }
    }

    /**
     * Keep the y value constant and increment the x value
     */
    private fun plotPointsRight() {
        val horizontalPixels = Math.abs(a!!.x - b!!.x).toDouble()
        pointArr = arrayOfNulls(horizontalPixels.toInt())

        // create new points in array with y values filled
        var i = 0
        while (i < horizontalPixels) {
            point = Point()
            point!!.x = a!!.x + i + 1
            point!!.y = a!!.y
            pointArr[i] = point
            i++
        }
    }

    /**
     * Keep the y value constant and decrement the x value
     */
    private fun plotPointsLeft() {
        val horizontalPixels = Math.abs(a!!.x - b!!.x).toDouble()
        pointArr = arrayOfNulls(horizontalPixels.toInt())

        // create new points in array with y values filled
        var i = 0
        while (i < horizontalPixels) {
            point = Point()
            point!!.x = a!!.x - i - 1
            point!!.y = a!!.y
            pointArr[i] = point
            i++
        }
    }

    /** Vertical: x stays put, y decreases. */
    private fun plotPointsUp() {
        var tempY = a!!.y
        val tempX = a!!.x
        val verticalPixels = Math.abs(a!!.y - b!!.y).toDouble()
        pointArr = arrayOfNulls(verticalPixels.toInt())
        var i = 0
        while (i < verticalPixels) {
            point = Point()
            tempY--
            point!!.x = tempX
            point!!.y = tempY
            // add point to list
            pointArr[i] = point
            i++
        }
    }

    /** Diagonal up-right: step the long axis, interpolate the short one. */
    private fun plotPointsUpRight() {
        val verticalPixels = Math.abs(a!!.y - b!!.y).toDouble()
        val horizontalPixels = Math.abs(a!!.x - b!!.x).toDouble()
        if (horizontalPixels == verticalPixels) {
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            var i = 0
            while (i < horizontalPixels) {
                point = Point()
                pointArr[i] = point
                pointArr[i]!!.x = a!!.x + i + 1
                pointArr[i]!!.y = a!!.y - i - 1
                i++
            }
        } else if (horizontalPixels >= verticalPixels) {
            // more horizontal moves
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            // create new points in array with x values filled
            run {
                var i = 0
                while (i < horizontalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.x = a!!.x + i + 1
                    i++
                }
            }

            // set first and last y
            pointArr[0]!!.y = a!!.y
            pointArr[pointArr.size - 1]!!.y = b!!.y

            // start filling in missing values
            val startValue = pointArr[0]!!.y
            val distance = Math.abs(a!!.y - b!!.y).toDouble()
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc -= distancePerMove
                pointArr[i]!!.y = Math.round(acc)
                i++
            }
        } else {
            pointArr = arrayOfNulls(verticalPixels.toInt())

            // create new points in array with y values filled
            run {
                var i = 0
                while (i < verticalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.y = a!!.y - i - 1
                    i++
                }
            }

            // set first and last x
            pointArr[0]!!.x = a!!.x
            pointArr[pointArr.size - 1]!!.x = b!!.x

            // start filling in missing values
            val startValue = pointArr[0]!!.x
            val distance = Math.abs(a!!.x - b!!.x).toDouble()
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc += distancePerMove
                pointArr[i]!!.x = Math.round(acc)
                i++
            }
        }
    }

    /** Diagonal up-left: step the long axis, interpolate the short one. */
    private fun plotPointsUpLeft() {
        val verticalPixels = Math.abs(a!!.y - b!!.y).toDouble()
        val horizontalPixels = Math.abs(a!!.x - b!!.x).toDouble()
        if (horizontalPixels == verticalPixels) {
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            var i = 0
            while (i < horizontalPixels) {
                point = Point()
                pointArr[i] = point
                pointArr[i]!!.x = a!!.x - i - 1
                pointArr[i]!!.y = a!!.y - i - 1
                i++
            }
        } else if (horizontalPixels >= verticalPixels) {
            // more horizontal moves
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            // create new points in array with x values filled
            run {
                var i = 0
                while (i < horizontalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.x = a!!.x - i - 1
                    i++
                }
            }

            // set first and last y
            pointArr[0]!!.y = a!!.y
            pointArr[pointArr.size - 1]!!.y = b!!.y

            // start filling in missing values
            val startValue = pointArr[0]!!.y
            val distance = Math.abs(a!!.y - b!!.y).toDouble()
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc -= distancePerMove
                pointArr[i]!!.y = Math.round(acc)
                i++
            }
        } else {
            pointArr = arrayOfNulls(verticalPixels.toInt())

            // create new points in array with y values filled
            run {
                var i = 0
                while (i < verticalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.y = a!!.y - 1 - i
                    i++
                }
            }

            // set first and last x
            pointArr[0]!!.x = a!!.x
            pointArr[pointArr.size - 1]!!.x = b!!.x

            // start filling in missing values
            val startValue = pointArr[0]!!.x
            val distance = Math.abs(a!!.x - b!!.x).toDouble()
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc -= distancePerMove
                pointArr[i]!!.x = Math.round(acc)
                i++
            }
        }
    }

    /** Vertical: x stays put, y increases. */
    private fun plotPointsDown() {
        var tempY = a!!.y
        val tempX = a!!.x
        val verticalPixels = Math.abs(b!!.y - a!!.y).toDouble()
        pointArr = arrayOfNulls(verticalPixels.toInt())
        var i = 0
        while (i < verticalPixels) {
            point = Point()
            tempY++
            point!!.x = tempX
            point!!.y = tempY
            // add point to list
            pointArr[i] = point
            i++
        }
    }

    /** Diagonal down-right: step the long axis, interpolate the short one. */
    private fun plotPointsDownRight() {
        val verticalPixels = Math.abs(a!!.y - b!!.y).toDouble()
        val horizontalPixels = Math.abs(a!!.x - b!!.x).toDouble()
        if (horizontalPixels == verticalPixels) {
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            var i = 0
            while (i < horizontalPixels) {
                point = Point()
                pointArr[i] = point
                pointArr[i]!!.x = a!!.x + i + 1
                pointArr[i]!!.y = a!!.y + i + 1
                i++
            }
        } else if (horizontalPixels >= verticalPixels) {
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            // create new points in array with y values filled
            run {
                var i = 0
                while (i < horizontalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.x = a!!.x + i + 1
                    i++
                }
            }

            // set first and last y
            pointArr[0]!!.y = a!!.y
            pointArr[pointArr.size - 1]!!.y = b!!.y

            // start filling in missing values
            val startValue = pointArr[0]!!.y
            val endValue = pointArr[pointArr.size - 1]!!.y
            val distance = Math.abs(endValue - startValue)
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc += distancePerMove
                pointArr[i]!!.y = Math.round(acc)
                i++
            }
        } else {
            pointArr = arrayOfNulls(verticalPixels.toInt())

            // create new points in array with y values filled
            run {
                var i = 0
                while (i < verticalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.y = a!!.y + i + 1
                    i++
                }
            }

            // set first and last x
            pointArr[0]!!.x = a!!.x
            pointArr[pointArr.size - 1]!!.x = b!!.x
            // start filling in missing values
            val startValue = pointArr[0]!!.x
            val endValue = pointArr[pointArr.size - 1]!!.x
            val distance = Math.abs(endValue - startValue)
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc += distancePerMove
                pointArr[i]!!.x = Math.round(acc)
                i++
            }
        }
    }

    /** Diagonal down-left: step the long axis, interpolate the short one. */
    private fun plotPointsDownLeft() {
        val verticalPixels = Math.abs(a!!.y - b!!.y).toDouble()
        val horizontalPixels = Math.abs(a!!.x - b!!.x).toDouble()
        if (horizontalPixels == verticalPixels) {
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            var i = 0
            while (i < horizontalPixels) {
                point = Point()
                pointArr[i] = point
                pointArr[i]!!.x = a!!.x - i - 1
                pointArr[i]!!.y = a!!.y + i + 1
                i++
            }
        } else if (horizontalPixels >= verticalPixels) {
            pointArr = arrayOfNulls(horizontalPixels.toInt())
            // create new points in array with y values filled
            run {
                var i = 0
                while (i < horizontalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.x = a!!.x - i - 1
                    i++
                }
            }

            // set first and last y
            pointArr[0]!!.y = a!!.y
            pointArr[pointArr.size - 1]!!.y = b!!.y

            // start filling in missing values
            val startValue = pointArr[0]!!.y
            val endValue = pointArr[pointArr.size - 1]!!.y
            val distance = Math.abs(endValue - startValue)
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc += distancePerMove
                pointArr[i]!!.y = Math.round(acc)
                i++
            }
        } else {
            pointArr = arrayOfNulls(verticalPixels.toInt())

            // create new points in array with y values filled
            run {
                var i = 0
                while (i < verticalPixels) {
                    point = Point()
                    pointArr[i] = point
                    pointArr[i]!!.y = a!!.y + i + 1
                    i++
                }
            }

            // set first and last x
            pointArr[0]!!.x = a!!.x
            pointArr[pointArr.size - 1]!!.x = b!!.x
            // start filling in missing values
            val startValue = pointArr[0]!!.x
            val endValue = pointArr[pointArr.size - 1]!!.x
            val distance = Math.abs(endValue - startValue)
            val distancePerMove = distance.toFloat() / (pointArr.size - 1).toFloat()
            var acc = startValue.toFloat()
            var i = 1
            while (i < pointArr.size - 1) {
                acc -= distancePerMove
                pointArr[i]!!.x = Math.round(acc)
                i++
            }
        }
    }
}