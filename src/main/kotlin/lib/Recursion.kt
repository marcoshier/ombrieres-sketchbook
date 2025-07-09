package lib


fun recurse(node: Branch, action: (Branch) -> Unit) {
    action(node)
    for (child in node.children) {
        recurse(child, action)
    }
}

fun flattenTree(root: Branch): MutableList<Branch> {
    val pds = mutableListOf<Branch>()
    recurse(root) { pds.add(it) }
    return pds
}