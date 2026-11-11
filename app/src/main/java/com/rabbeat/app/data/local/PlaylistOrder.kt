package com.rabbeat.app.data.local

internal fun reorderedPositions(
    existingRefIdsInOrder: List<String>,
    orderedVisibleIds: List<String>,
): List<String> {
    val existing = existingRefIdsInOrder.toSet()
    val given = orderedVisibleIds.distinct()
    val givenSet = given.toSet()
    val visible = given.filter { it in existing }
    val hidden = existingRefIdsInOrder.filter { it !in givenSet }
    return visible + hidden
}
