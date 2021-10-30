package org.nypl.simplified.vanilla.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.nypl.simplified.vanilla.R
import org.nypl.simplified.vanilla.databinding.CatalogFragmentBinding
import org.slf4j.LoggerFactory

class CatalogFragment : Fragment(), SearchView.OnQueryTextListener {

  private val viewModel: CatalogViewModel by viewModels()

  private val logger = LoggerFactory.getLogger(CatalogActivity::class.java)

  private lateinit var binding: CatalogFragmentBinding
  private lateinit var catalogItemAdapter: CatalogItemAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding =
      DataBindingUtil.inflate(inflater, R.layout.catalog_fragment, container, false)
    binding.lifecycleOwner = this
    binding.viewModel = viewModel
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    catalogItemAdapter = CatalogItemAdapter()
    binding.catalogFragmentList.apply {
      adapter = catalogItemAdapter
      layoutManager = LinearLayoutManager(requireContext())
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.searchStatus.collect {
          logger.debug("New state: $it")
          when(it) {
            is SearchState.Loading -> {}
            is SearchState.Finished -> catalogItemAdapter.submitList(it.books)
          }
        }
      }
    }

    binding.catalogFragmentSearch.apply {
      setOnQueryTextListener(this@CatalogFragment)
    }
  }

  override fun onQueryTextSubmit(query: String?): Boolean {
    return query?.let {
      viewModel.submitSearch(query)
      binding.catalogFragmentSearch.clearFocus()
      true
    } ?: false
  }

  override fun onQueryTextChange(newText: String?): Boolean {
    // Leaving out list population while typing for now
    return true
  }
}
