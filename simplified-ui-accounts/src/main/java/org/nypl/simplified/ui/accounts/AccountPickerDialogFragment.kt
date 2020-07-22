package org.nypl.simplified.ui.accounts

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.navigation.api.NavigationControllers
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountPickerAdapter.OnAccountClickListener
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.slf4j.LoggerFactory

/**
 * Present a dialog that shows a list of all active accounts for the current profile.
 */

class AccountPickerDialogFragment : BottomSheetDialogFragment(), OnAccountClickListener {

  private val logger = LoggerFactory.getLogger(AccountPickerDialogFragment::class.java)

  private lateinit var recyclerView: RecyclerView
  private lateinit var imageLoader: ImageLoaderType
  private lateinit var profilesController: ProfilesControllerType
  private lateinit var accounts: List<AccountType>

  companion object {
    private const val ARG_CURRENT_ID = "org.nypl.simplified.ui.accounts.CURRENT_ID"

    fun create(
      currentId: AccountID
    ): AccountPickerDialogFragment {
      return AccountPickerDialogFragment().apply {
        arguments = Bundle().apply {
          putSerializable(ARG_CURRENT_ID, currentId)
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val services = Services.serviceDirectory()
    imageLoader = services.requireService(ImageLoaderType::class.java)
    profilesController = services.requireService(ProfilesControllerType::class.java)

    val accountsMap =
      profilesController.profileCurrent().accounts()

    accounts = accountsMap.values.toList()
      .sortedWith(AccountComparator())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.account_picker, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val currentId = requireArguments().getSerializable(ARG_CURRENT_ID) as AccountID

    recyclerView = view.findViewById(R.id.recyclerView)
    recyclerView.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(requireContext())
      adapter =
        AccountPickerAdapter(accounts, currentId, imageLoader, this@AccountPickerDialogFragment)
    }
  }

  override fun onAccountClick(account: AccountType) {
    this.logger.debug("selected account id={}, name={}", account.id, account.provider.displayName)

    // Note: In the future consider refactoring this dialog to return a result via
    //       setFragmentResultListener to decouple it from the profiles logic.
    val profile = profilesController.profileCurrent()
    val newPreferences = profile
      .preferences()
      .copy(
        mostRecentAccount = account.id
      )
    profilesController.profileUpdate { it.copy(preferences = newPreferences) }
    dismiss()
  }

  override fun onAddAccountClick() {
    val navigationController = NavigationControllers.find(
      activity = this.requireActivity(),
      interfaceType = AccountNavigationControllerType::class.java
    )
    navigationController.openSettingsAccountRegistry()
    dismiss()
  }
}

class AccountViewHolder(
  view: View,
  private val imageLoader: ImageLoaderType,
  private val listener: OnAccountClickListener
) : RecyclerView.ViewHolder(view) {
  private val titleView: TextView = view.findViewById(R.id.accountTitle)
  private val iconView: ImageView = view.findViewById(R.id.accountIcon)

  var account: AccountType? = null

  init {
    view.setOnClickListener {
      account?.let { listener.onAccountClick(it) }
    }
  }

  fun bind(account: AccountType, isCurrent: Boolean) {
    this.account = account

    titleView.text = account.provider.displayName
    titleView.typeface = if (isCurrent) {
      Typeface.DEFAULT_BOLD
    } else {
      Typeface.DEFAULT
    }

    ImageAccountIcons.loadAccountLogoIntoView(
      loader = this.imageLoader.loader,
      account = account.provider.toDescription(),
      defaultIcon = R.drawable.account_default,
      iconView = iconView
    )
  }
}

class FooterViewHolder(
  view: View,
  private val listener: OnAccountClickListener
) : RecyclerView.ViewHolder(view) {

  init {
    val titleView: TextView = view.findViewById(R.id.accountTitle)
    titleView.setText(R.string.accountAdd)

    val iconView: ImageView = view.findViewById(R.id.accountIcon)
    iconView.colorFilter =
      PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
    iconView.setImageResource(R.drawable.ic_add)

    view.setOnClickListener {
      listener.onAddAccountClick()
    }
  }
}

class AccountPickerAdapter(
  private val accounts: List<AccountType>,
  private val currentId: AccountID,
  private val imageLoader: ImageLoaderType,
  private val listener: OnAccountClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  companion object {
    private const val LIST_ITEM = 1
    private const val LIST_FOOTER = 2
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val view = inflater.inflate(R.layout.account_picker_item, parent, false)
    return when (viewType) {
      LIST_FOOTER -> FooterViewHolder(view, listener)
      else -> AccountViewHolder(view, imageLoader, listener)
    }
  }

  override fun getItemCount() = accounts.size + 1 // Include the 'add account' footer

  override fun getItemViewType(position: Int) = when (position) {
    accounts.size -> LIST_FOOTER
    else -> LIST_ITEM
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (holder.itemViewType) {
      LIST_ITEM -> {
        val item = accounts[position]
        (holder as AccountViewHolder).bind(item, item.id == currentId)
      }
    }
  }

  interface OnAccountClickListener {
    fun onAccountClick(account: AccountType)
    fun onAddAccountClick()
  }
}
