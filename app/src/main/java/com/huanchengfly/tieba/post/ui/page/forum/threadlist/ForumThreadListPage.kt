package com.huanchengfly.tieba.post.ui.page.forum.threadlist

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.activities.ThreadActivity
import com.huanchengfly.tieba.post.api.abstractText
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.frsPage.Classify
import com.huanchengfly.tieba.post.arch.BaseComposeActivity.Companion.LocalWindowSizeClass
import com.huanchengfly.tieba.post.arch.ImmutableHolder
import com.huanchengfly.tieba.post.arch.collectPartialAsState
import com.huanchengfly.tieba.post.arch.onEvent
import com.huanchengfly.tieba.post.arch.pageViewModel
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
import com.huanchengfly.tieba.post.ui.page.forum.getSortType
import com.huanchengfly.tieba.post.ui.widgets.Chip
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCard
import com.huanchengfly.tieba.post.ui.widgets.compose.LazyLoad
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalSnackbarHostState
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import kotlinx.coroutines.flow.Flow

private fun getFirstLoadIntent(
    context: Context,
    forumName: String,
    isGood: Boolean = false,
): ForumThreadListUiIntent {
    return if (isGood) ForumThreadListUiIntent.Refresh(forumName, -1, 0)
    else ForumThreadListUiIntent.FirstLoad(forumName, getSortType(context, forumName), null)
}

private fun getRefreshIntent(
    context: Context,
    forumName: String,
    isGood: Boolean = false,
    sortType: Int = getSortType(context, forumName),
    goodClassifyId: Int? = if (isGood) 0 else null,
): ForumThreadListUiIntent {
    return if (isGood) ForumThreadListUiIntent.Refresh(forumName, -1, goodClassifyId)
    else ForumThreadListUiIntent.Refresh(forumName, sortType, null)
}

private fun getLoadMoreIntent(
    context: Context,
    forumId: Long,
    forumName: String,
    page: Int,
    threadListIds: List<Long>,
    isGood: Boolean = false,
): ForumThreadListUiIntent {
    return if (isGood) ForumThreadListUiIntent.LoadMore(forumId, forumName, page, threadListIds, 0)
    else ForumThreadListUiIntent.LoadMore(
        forumId,
        forumName,
        page,
        threadListIds,
        getSortType(context, forumName)
    )
}

private enum class ItemType {
    Top, PlainText, SingleMedia, MultiMedia, Video
}

@Composable
private fun GoodClassifyTabs(
    goodClassifyHoldersProvider: () -> List<ImmutableHolder<Classify>>,
    selectedItem: Int?,
    onSelected: (Int) -> Unit,
) {
    val goodClassifyHolders = goodClassifyHoldersProvider()
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        goodClassifyHolders.forEach { holder ->
            val (classify) = holder
            Chip(
                text = classify.class_name,
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .clickable {
                        onSelected(classify.class_id)
                    },
                invertColor = selectedItem == classify.class_id
            )
        }
    }
}

@Composable
private fun ThreadList(
    state: LazyListState,
    itemHoldersProvider: () -> List<ImmutableHolder<ThreadInfo>>,
    isGood: Boolean,
    goodClassifyId: Int?,
    goodClassifyHoldersProvider: () -> List<ImmutableHolder<Classify>>,
    onItemClicked: (ThreadInfo) -> Unit,
    onAgree: (ThreadInfo) -> Unit,
    onClassifySelected: (Int) -> Unit
) {
    val itemHolders = itemHoldersProvider()
    LazyColumn(
        state = state,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        if (isGood) {
            item(key = "GoodClassifyHeader") {
                GoodClassifyTabs(
                    goodClassifyHoldersProvider = goodClassifyHoldersProvider,
                    selectedItem = goodClassifyId,
                    onSelected = onClassifySelected
                )
            }
        }
        itemsIndexed(
            items = itemHolders,
            key = { index, holder ->
                val (item) = holder
                "${index}_${item.id}"
            },
            contentType = { _, holder ->
                val (item) = holder
                if (item.isTop == 1) ItemType.Top
                else {
                    if (item.media.isNotEmpty())
                        if (item.media.size == 1) ItemType.SingleMedia else ItemType.MultiMedia
                    else if (item.videoInfo != null)
                        ItemType.Video
                    else ItemType.PlainText
                }
            }
        ) { index, holder ->
            val (item) = holder
            val windowSizeClass = LocalWindowSizeClass.current
            val fraction = when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Expanded -> 0.5f
                else -> 1f
            }
            Column(
                modifier = Modifier.fillMaxWidth(fraction)
            ) {
                if (item.isTop == 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onItemClicked(item)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Chip(
                            text = stringResource(id = R.string.content_top),
                            shape = RoundedCornerShape(3.dp)
                        )
                        var title = item.title
                        if (title.isBlank()) {
                            title = item.abstractText
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.subtitle2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    if (index > 0) {
                        if (itemHolders[index - 1].item.isTop == 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    FeedCard(
                        item = holder,
                        onClick = {
                            onItemClicked(item)
                        },
                        onAgree = {
                            onAgree(item)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ForumThreadListPage(
    forumId: Long,
    forumName: String,
    eventFlow: Flow<ForumThreadListUiEvent>,
    isGood: Boolean = false,
    lazyListState: LazyListState = rememberLazyListState(),
    viewModel: ForumThreadListViewModel = if (isGood) pageViewModel<GoodThreadListViewModel>() else pageViewModel<LatestThreadListViewModel>()
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    LazyLoad(loaded = viewModel.initialized) {
        viewModel.send(getFirstLoadIntent(context, forumName, isGood))
        viewModel.initialized = true
    }
    eventFlow.onEvent<ForumThreadListUiEvent.Refresh> {
        viewModel.send(getRefreshIntent(context, forumName, isGood, it.sortType))
    }
    eventFlow.onEvent<ForumThreadListUiEvent.BackToTop> {
        lazyListState.animateScrollToItem(0)
    }
    viewModel.onEvent<ForumThreadListUiEvent.AgreeFail> {
        val snackbarResult = snackbarHostState.showSnackbar(
            message = context.getString(
                R.string.snackbar_agree_fail,
                it.errorCode,
                it.errorMsg
            ),
            actionLabel = context.getString(R.string.button_retry)
        )

        if (snackbarResult == SnackbarResult.ActionPerformed) {
            viewModel.send(
                ForumThreadListUiIntent.Agree(
                    it.threadId,
                    it.postId,
                    it.hasAgree
                )
            )
        }
    }
    val isRefreshing by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::isRefreshing,
        initial = false
    )
    val isLoadingMore by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::isLoadingMore,
        initial = false
    )
    val hasMore by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::hasMore,
        initial = true
    )
    val currentPage by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::currentPage,
        initial = 1
    )
    val threadList by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::threadList,
        initial = emptyList()
    )
    val threadListIds by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::threadListIds,
        initial = emptyList()
    )
    val goodClassifyId by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::goodClassifyId,
        initial = null
    )
    val goodClassifies by viewModel.uiState.collectPartialAsState(
        prop1 = ForumThreadListUiState::goodClassifies,
        initial = emptyList()
    )
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.send(getRefreshIntent(context, forumName, isGood)) }
    )
    Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
        LoadMoreLayout(
            isLoading = isLoadingMore,
            onLoadMore = {
                viewModel.send(
                    getLoadMoreIntent(
                        context,
                        forumId,
                        forumName,
                        currentPage,
                        threadListIds,
                        isGood
                    )
                )
            },
            loadEnd = !hasMore
        ) {
            ThreadList(
                state = lazyListState,
                itemHoldersProvider = { threadList },
                isGood = isGood,
                goodClassifyId = goodClassifyId,
                goodClassifyHoldersProvider = { goodClassifies },
                onItemClicked = {
                    ThreadActivity.launch(
                        context,
                        it.threadId.toString()
                    )
                },
                onAgree = {
                    viewModel.send(
                        ForumThreadListUiIntent.Agree(
                            it.threadId,
                            it.firstPostId,
                            it.agree?.hasAgree ?: 0
                        )
                    )
                },
                onClassifySelected = {
                    viewModel.send(
                        getRefreshIntent(
                            context,
                            forumName,
                            true,
                            goodClassifyId = it
                        )
                    )
                }
            )
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}