package org.wikipedia.usercontrib

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.activity.BaseActivity
import org.wikipedia.databinding.ActivityUserContribWikiSelectBinding
import org.wikipedia.notifications.NotificationFilterActivity
import org.wikipedia.page.Namespace
import org.wikipedia.settings.Prefs
import org.wikipedia.settings.languages.WikipediaLanguagesActivity
import org.wikipedia.staticdata.TalkAliasData
import org.wikipedia.staticdata.UserAliasData
import org.wikipedia.staticdata.UserTalkAliasData
import org.wikipedia.views.DefaultViewHolder

class UserContribFilterActivity : BaseActivity() {

    private lateinit var binding: ActivityUserContribWikiSelectBinding
    private lateinit var selectLangCode: String

    private val langUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        binding.recyclerView.adapter = ItemAdapter(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserContribWikiSelectBinding.inflate(layoutInflater)
        selectLangCode = intent.getStringExtra(INTENT_EXTRA_SELECT_LANG_CODE).orEmpty().ifEmpty { WikipediaApp.instance.appOrSystemLanguageCode }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = ItemAdapter(this)
        setContentView(binding.root)
    }

    inner class ItemViewHolder constructor(itemView: UserContribFilterItemView) :
        DefaultViewHolder<UserContribFilterItemView>(itemView) {
        fun bindItem(item: Item) {
            view.setContents(item)
        }
    }

    private inner class AddLanguageViewHolder constructor(itemView: UserContribFilterItemView) :
            DefaultViewHolder<UserContribFilterItemView>(itemView), UserContribFilterItemView.Callback {
        fun bindItem(text: String) {
            (itemView as UserContribFilterItemView).callback = this
            itemView.setSingleLabel(text)
        }

        override fun onSelected(item: Item?) {
            langUpdateLauncher.launch(WikipediaLanguagesActivity.newIntent(this@UserContribFilterActivity, Constants.InvokeSource.USER_CONTRIB_ACTIVITY))
        }
    }

    private inner class ItemAdapter(val context: Context) : RecyclerView.Adapter<DefaultViewHolder<*>>(), UserContribFilterItemView.Callback {
        private val itemList = mutableListOf<Any>()

        init {
            itemList.add(getString(R.string.notifications_wiki_filter_header))
            WikipediaApp.instance.languageState.appLanguageCodes.forEach { itemList.add(Item(FILTER_TYPE_WIKI, it, null)) }
            itemList.add(Item(FILTER_TYPE_WIKI, Constants.WIKI_CODE_COMMONS, R.drawable.ic_commons_logo))
            itemList.add(Item(FILTER_TYPE_WIKI, Constants.WIKI_CODE_WIKIDATA, R.drawable.ic_wikidata_logo))
            itemList.add(getString(R.string.notifications_filter_update_app_languages))
            itemList.add(getString(R.string.user_contrib_filter_ns_header))
            itemList.add(Item(FILTER_TYPE_NAMESPACE, getString(R.string.user_contrib_filter_all), R.drawable.ic_mode_edit_white_24dp))
            itemList.add(Item(FILTER_TYPE_NAMESPACE, getString(R.string.namespace_article), R.drawable.ic_article_ltr_ooui))
            itemList.add(Item(FILTER_TYPE_NAMESPACE, TalkAliasData.valueFor(WikipediaApp.instance.appOrSystemLanguageCode), R.drawable.ic_notification_article_talk))
            itemList.add(Item(FILTER_TYPE_NAMESPACE, UserAliasData.valueFor(WikipediaApp.instance.appOrSystemLanguageCode), R.drawable.ic_user_avatar))
            itemList.add(Item(FILTER_TYPE_NAMESPACE, UserTalkAliasData.valueFor(WikipediaApp.instance.appOrSystemLanguageCode), R.drawable.ic_notification_user_talk))
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): DefaultViewHolder<*> {
            return when (type) {
                VIEW_TYPE_ADD_LANGUAGE -> {
                    AddLanguageViewHolder(UserContribFilterItemView(context))
                }
                else -> {
                    val view = UserContribFilterItemView(context)
                    view.callback = this
                    ItemViewHolder(view)
                }
            }
        }

        override fun getItemCount(): Int {
            return itemList.size
        }

        override fun getItemViewType(position: Int): Int {
            return if (itemList[position] is String && itemList[position] == getString(R.string.notifications_filter_update_app_languages)) VIEW_TYPE_ADD_LANGUAGE
            else if (itemList[position] is String) VIEW_TYPE_HEADER
            else VIEW_TYPE_ITEM
        }

        override fun onBindViewHolder(holder: DefaultViewHolder<*>, position: Int) {
            when (holder) {
                is AddLanguageViewHolder -> holder.bindItem(itemList[position] as String)
                else -> (holder as ItemViewHolder).bindItem(itemList[position] as Item)
            }
        }

        override fun onSelected(item: Item?) {
            item?.let {
                if (it.type == FILTER_TYPE_WIKI) {
                    Prefs.userContribFilterLangCode = item.filterCode
                } else if (it.type == FILTER_TYPE_NAMESPACE) {
                    when (val namespaceCode = getNamespaceCode(item.filterCode)) {
                        -1 -> { // Select "all"
                            if (Prefs.userContribFilterNs.isEmpty()) {
                                Prefs.userContribFilterNs.plus(listOf(
                                    Namespace.MAIN.code(),
                                    Namespace.TALK.code(),
                                    Namespace.USER.code(),
                                    Namespace.USER_TALK.code()
                                ))
                            } else {
                                Prefs.userContribFilterNs = emptySet()
                            }
                        }
                        else -> {
                            if (Prefs.userContribFilterNs.contains(namespaceCode)) {
                                Prefs.userContribFilterNs.minus(namespaceCode)
                            } else {
                                Prefs.userContribFilterNs.plus(namespaceCode)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getNamespaceCode(text: String): Int {
        return when (text) {
            getString(R.string.namespace_article) -> Namespace.MAIN.code()
            TalkAliasData.valueFor(WikipediaApp.instance.appOrSystemLanguageCode) -> Namespace.TALK.code()
            UserAliasData.valueFor(WikipediaApp.instance.appOrSystemLanguageCode) -> Namespace.USER.code()
            UserTalkAliasData.valueFor(WikipediaApp.instance.appOrSystemLanguageCode) -> Namespace.USER_TALK.code()
            else -> -1
        }
    }

    inner class Item constructor(val type: Int, val filterCode: String, val imageRes: Int? = null) {
        fun isEnabled(): Boolean {
            val excludedWikiCodes = Prefs.notificationExcludedWikiCodes
            val excludedTypeCodes = Prefs.notificationExcludedTypeCodes
            if (filterCode == getString(R.string.notifications_all_types_text)) {
                return NotificationFilterActivity.allTypesIdList().find { excludedTypeCodes.contains(it) } == null
            }
            return !excludedWikiCodes.contains(filterCode) && !excludedTypeCodes.contains(filterCode)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_ADD_LANGUAGE = 2
        private const val FILTER_TYPE_WIKI = 0
        private const val FILTER_TYPE_NAMESPACE = 1

        const val INTENT_EXTRA_SELECT_LANG_CODE = "selectLangCode"

        fun newIntent(context: Context, selectLangCode: String): Intent {
            return Intent(context, UserContribFilterActivity::class.java)
                    .putExtra(INTENT_EXTRA_SELECT_LANG_CODE, selectLangCode)
        }
    }
}
