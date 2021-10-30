package org.nypl.simplified.vanilla.catalog

import android.view.View
import android.widget.ProgressBar
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView

@BindingAdapter("showWhenFinished")
fun RecyclerView.showWhenFinished(searchState: SearchState?) {
  searchState?.let {
    visibility = when (it) {
      is SearchState.Finished -> View.VISIBLE
      SearchState.Loading -> View.GONE
    }
  }
}

@BindingAdapter("showWhenLoading")
fun ProgressBar.showWhenLoading(searchState: SearchState?) {
  searchState?.let {
    visibility = when (it) {
      is SearchState.Finished -> View.GONE
      SearchState.Loading -> View.VISIBLE
    }
  }
}
