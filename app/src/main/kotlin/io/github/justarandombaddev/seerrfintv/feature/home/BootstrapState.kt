package io.github.justarandombaddev.seerrfintv.feature.home

enum class BootstrapAction(val label: String) {
    FocusOne("Focus 1"),
    FocusTwo("Focus 2"),
    FocusThree("Focus 3"),
}

data class BootstrapState(
    val activeAction: BootstrapAction? = null,
    val status: String = "Choisissez un élément avec la télécommande",
) {
    fun activate(action: BootstrapAction): BootstrapState =
        copy(
            activeAction = action,
            status = "${action.label} activé",
        )
}
