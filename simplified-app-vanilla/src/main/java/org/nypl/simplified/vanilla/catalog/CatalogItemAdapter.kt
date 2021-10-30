package org.nypl.simplified.vanilla.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.nypl.simplified.vanilla.databinding.CatalogFragmentListItemBinding

class CatalogItemAdapter : ListAdapter<BookItem, BookItemViewHolder>(Companion){

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookItemViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val binding = CatalogFragmentListItemBinding.inflate(inflater, parent, false)
    return BookItemViewHolder(binding)
  }

  override fun onBindViewHolder(holder: BookItemViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  companion object : DiffUtil.ItemCallback<BookItem>() {
    override fun areItemsTheSame(oldItem: BookItem, newItem: BookItem): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: BookItem, newItem: BookItem): Boolean {
      return oldItem == newItem
    }
  }
}

class BookItemViewHolder(private val binding: CatalogFragmentListItemBinding) : RecyclerView.ViewHolder(binding.root) {
  fun bind(book: BookItem) {
    binding.catalogFragmentListItemTitle.text = book.title
  }
}

data class BookItem(
  val title: String
)
