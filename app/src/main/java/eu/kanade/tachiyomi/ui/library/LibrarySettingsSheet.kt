package eu.kanade.tachiyomi.ui.library

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import uy.kohesive.injekt.injectLazy

class LibrarySettingsSheet(
    activity: Activity,
    onGroupClickListener: (ExtendedNavigationView.Group) -> Unit
) : BottomSheetDialog(activity) {

    private var navView: Settings

    init {
        navView = Settings(activity)
        navView.onGroupClicked = onGroupClickListener

        setContentView(navView)
    }

    fun hasActiveFilters(): Boolean {
        return navView.hasActiveFilters()
    }

    /**
     * The navigation view shown in the sheet with the different options to show the library.
     */
    class Settings @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        ExtendedNavigationView(context, attrs) {

        private val preferences: PreferencesHelper by injectLazy()

        /**
         * List of groups shown in the view.
         */
        private val groups = listOf(FilterGroup(), SortGroup(), DisplayGroup(), BadgeGroup())

        /**
         * Adapter instance.
         */
        private val adapter = Adapter(groups.map { it.createItems() }.flatten())

        /**
         * Click listener to notify the parent fragment when an item from a group is clicked.
         */
        var onGroupClicked: (Group) -> Unit = {}

        init {
            recycler.adapter = adapter
            addView(recycler)

            groups.forEach { it.initModels() }
        }

        /**
         * Returns true if there's at least one filter from [FilterGroup] active.
         */
        fun hasActiveFilters(): Boolean {
            return (groups[0] as FilterGroup).items.any { it.checked }
        }

        /**
         * Adapter of the recycler view.
         */
        inner class Adapter(items: List<Item>) : ExtendedNavigationView.Adapter(items) {

            override fun onItemClicked(item: Item) {
                if (item is GroupedItem) {
                    item.group.onItemClicked(item)
                    onGroupClicked(item.group)
                }
            }
        }

        /**
         * Filters group (unread, downloaded, ...).
         */
        inner class FilterGroup : Group {

            private val downloaded = Item.CheckboxGroup(R.string.action_filter_downloaded, this)

            private val unread = Item.CheckboxGroup(R.string.action_filter_unread, this)

            private val completed = Item.CheckboxGroup(R.string.completed, this)

            override val items = listOf(downloaded, unread, completed)

            override val header = Item.Header(R.string.action_filter)

            override val footer = Item.Separator()

            override fun initModels() {
                downloaded.checked = preferences.downloadedOnly().get() || preferences.filterDownloaded().get()
                downloaded.enabled = !preferences.downloadedOnly().get()
                unread.checked = preferences.filterUnread().get()
                completed.checked = preferences.filterCompleted().get()
            }

            override fun onItemClicked(item: Item) {
                item as Item.CheckboxGroup
                item.checked = !item.checked
                when (item) {
                    downloaded -> preferences.filterDownloaded().set(item.checked)
                    unread -> preferences.filterUnread().set(item.checked)
                    completed -> preferences.filterCompleted().set(item.checked)
                }

                adapter.notifyItemChanged(item)
            }
        }

        /**
         * Sorting group (alphabetically, by last read, ...) and ascending or descending.
         */
        inner class SortGroup : Group {

            private val alphabetically = Item.MultiSort(R.string.action_sort_alpha, this)

            private val total = Item.MultiSort(R.string.action_sort_total, this)

            private val lastRead = Item.MultiSort(R.string.action_sort_last_read, this)

            private val lastChecked = Item.MultiSort(R.string.action_sort_last_checked, this)

            private val unread = Item.MultiSort(R.string.action_filter_unread, this)

            private val latestChapter = Item.MultiSort(R.string.action_sort_latest_chapter, this)

            override val items =
                listOf(alphabetically, lastRead, lastChecked, unread, total, latestChapter)

            override val header = Item.Header(R.string.action_sort)

            override val footer = Item.Separator()

            override fun initModels() {
                val sorting = preferences.librarySortingMode().get()
                val order = if (preferences.librarySortingAscending().get())
                    Item.MultiSort.SORT_ASC else Item.MultiSort.SORT_DESC

                alphabetically.state =
                    if (sorting == LibrarySort.ALPHA) order else Item.MultiSort.SORT_NONE
                lastRead.state =
                    if (sorting == LibrarySort.LAST_READ) order else Item.MultiSort.SORT_NONE
                lastChecked.state =
                    if (sorting == LibrarySort.LAST_CHECKED) order else Item.MultiSort.SORT_NONE
                unread.state =
                    if (sorting == LibrarySort.UNREAD) order else Item.MultiSort.SORT_NONE
                total.state = if (sorting == LibrarySort.TOTAL) order else Item.MultiSort.SORT_NONE
                latestChapter.state =
                    if (sorting == LibrarySort.LATEST_CHAPTER) order else Item.MultiSort.SORT_NONE
            }

            override fun onItemClicked(item: Item) {
                item as Item.MultiStateGroup
                val prevState = item.state

                item.group.items.forEach {
                    (it as Item.MultiStateGroup).state =
                        Item.MultiSort.SORT_NONE
                }
                item.state = when (prevState) {
                    Item.MultiSort.SORT_NONE -> Item.MultiSort.SORT_ASC
                    Item.MultiSort.SORT_ASC -> Item.MultiSort.SORT_DESC
                    Item.MultiSort.SORT_DESC -> Item.MultiSort.SORT_ASC
                    else -> throw Exception("Unknown state")
                }

                preferences.librarySortingMode().set(
                    when (item) {
                        alphabetically -> LibrarySort.ALPHA
                        lastRead -> LibrarySort.LAST_READ
                        lastChecked -> LibrarySort.LAST_CHECKED
                        unread -> LibrarySort.UNREAD
                        total -> LibrarySort.TOTAL
                        latestChapter -> LibrarySort.LATEST_CHAPTER
                        else -> throw Exception("Unknown sorting")
                    }
                )
                preferences.librarySortingAscending().set(item.state == Item.MultiSort.SORT_ASC)

                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }
        }

        inner class BadgeGroup : Group {
            private val downloadBadge =
                Item.CheckboxGroup(R.string.action_display_download_badge, this)
            override val header = null
            override val footer = null
            override val items = listOf(downloadBadge)
            override fun initModels() {
                downloadBadge.checked = preferences.downloadBadge().get()
            }

            override fun onItemClicked(item: Item) {
                item as Item.CheckboxGroup
                item.checked = !item.checked
                preferences.downloadBadge().set((item.checked))
                adapter.notifyItemChanged(item)
            }
        }

        /**
         * Display group, to show the library as a list or a grid.
         */
        inner class DisplayGroup : Group {

            private val grid = Item.Radio(R.string.action_display_grid, this)

            private val list = Item.Radio(R.string.action_display_list, this)

            override val items = listOf(grid, list)

            override val header = Item.Header(R.string.action_display)

            override val footer = null

            override fun initModels() {
                val asList = preferences.libraryAsList().get()
                grid.checked = !asList
                list.checked = asList
            }

            override fun onItemClicked(item: Item) {
                item as Item.Radio
                if (item.checked) return

                item.group.items.forEach { (it as Item.Radio).checked = false }
                item.checked = true

                preferences.libraryAsList().set(item == list)

                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }
        }
    }
}
