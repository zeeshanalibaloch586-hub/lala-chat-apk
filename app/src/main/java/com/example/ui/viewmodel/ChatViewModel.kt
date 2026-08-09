package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepo = AuthRepository(application)
    private val chatRepo = ChatRepository(application)
    private val userRepo = UserRepository(application)

    val currentUser: StateFlow<User?> = authRepo.currentUser

    // Chats list
    val chats: StateFlow<List<Chat>> = chatRepo.getChatsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current active chat
    private val _activeChat = MutableStateFlow<Chat?>(null)
    val activeChat: StateFlow<Chat?> = _activeChat.asStateFlow()

    // Current chat messages
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // Typing status map
    val typingStatusMap: StateFlow<Map<String, Boolean>> = chatRepo.typingStatusMap

    // Search user results
    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Reply state
    private val _replyToMessage = MutableStateFlow<Message?>(null)
    val replyToMessage: StateFlow<Message?> = _replyToMessage.asStateFlow()

    // Block state
    val blockedUserIds: StateFlow<Set<String>> = userRepo.blockedUserIds

    fun openChatWithUser(otherUser: User, onOpened: (Chat) -> Unit) {
        val curr = currentUser.value ?: return
        viewModelScope.launch {
            val chat = chatRepo.getOrCreateChat(curr.userId, otherUser)
            _activeChat.value = chat
            chatRepo.startListeningToMessages(chat.chatId, curr.userId)

            // Collect active messages
            launch {
                chatRepo.getMessagesFlow(chat.chatId).collect { msgs ->
                    _messages.value = msgs
                }
            }
            onOpened(chat)
        }
    }

    fun openChatById(chatId: String) {
        val curr = currentUser.value ?: return
        viewModelScope.launch {
            _activeChat.value = chats.value.find { it.chatId == chatId }
            chatRepo.startListeningToMessages(chatId, curr.userId)

            launch {
                chatRepo.getMessagesFlow(chatId).collect { msgs ->
                    _messages.value = msgs
                }
            }
        }
    }

    fun closeActiveChat() {
        chatRepo.stopListeningToMessages()
        _activeChat.value = null
        _messages.value = emptyList()
        _replyToMessage.value = null
    }

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    chatRepo.startListeningToUserChats(user.userId)
                } else {
                    chatRepo.stopListeningToUserChats()
                }
            }
        }
    }

    fun searchUser(query: String) {
        val currId = currentUser.value?.userId ?: ""
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = userRepo.searchUser(query, currId)
            _isSearching.value = false
        }
    }

    fun setReplyMessage(message: Message?) {
        _replyToMessage.value = message
    }

    fun sendMessage(
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        mediaName: String? = null,
        voiceDurationMs: Long? = null
    ) {
        val chat = activeChat.value ?: return
        val curr = currentUser.value ?: return

        if (userRepo.isUserBlocked(chat.otherUserId)) {
            return
        }

        val reply = replyToMessage.value
        val replyId = reply?.messageId
        val replyText = reply?.content
        val replySender = if (reply?.senderId == curr.userId) "You" else chat.otherUserName

        _replyToMessage.value = null // clear reply banner

        viewModelScope.launch {
            chatRepo.sendMessage(
                chatId = chat.chatId,
                senderId = curr.userId,
                receiverId = chat.otherUserId,
                content = content,
                type = type,
                mediaUrl = mediaUrl,
                mediaName = mediaName,
                voiceDurationMs = voiceDurationMs,
                replyToMessageId = replyId,
                replyToContent = replyText,
                replyToSenderName = replySender
            )
        }
    }

    fun sendMediaMessage(
        mediaUri: Uri,
        type: MessageType,
        mediaName: String? = null,
        voiceDurationMs: Long? = null,
        caption: String = ""
    ) {
        val chat = activeChat.value ?: return
        val curr = currentUser.value ?: return

        if (userRepo.isUserBlocked(chat.otherUserId)) {
            return
        }

        val reply = replyToMessage.value
        val replyId = reply?.messageId
        val replyText = reply?.content
        val replySender = if (reply?.senderId == curr.userId) "You" else chat.otherUserName

        _replyToMessage.value = null

        viewModelScope.launch {
            chatRepo.sendMediaMessage(
                chatId = chat.chatId,
                senderId = curr.userId,
                receiverId = chat.otherUserId,
                type = type,
                mediaUri = mediaUri,
                mediaName = mediaName,
                voiceDurationMs = voiceDurationMs,
                caption = caption,
                replyToMessageId = replyId,
                replyToContent = replyText,
                replyToSenderName = replySender
            )
        }
    }

    fun retryMediaMessage(message: Message) {
        viewModelScope.launch {
            chatRepo.retryMediaMessage(message)
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val chat = activeChat.value ?: return
        val curr = currentUser.value ?: return
        chatRepo.setTypingStatus(chat.chatId, curr.userId, isTyping)
    }

    fun toggleReaction(message: Message, emoji: String) {
        val chat = activeChat.value ?: return
        val curr = currentUser.value ?: return
        viewModelScope.launch {
            chatRepo.toggleReaction(chat.chatId, message.messageId, curr.userId, emoji)
        }
    }

    fun deleteMessage(message: Message, deleteForEveryone: Boolean) {
        val chat = activeChat.value ?: return
        viewModelScope.launch {
            chatRepo.deleteMessage(chat.chatId, message.messageId, deleteForEveryone)
        }
    }

    fun forwardMessage(message: Message, targetChatId: String) {
        val curr = currentUser.value ?: return
        val targetChat = chats.value.find { it.chatId == targetChatId } ?: return
        viewModelScope.launch {
            chatRepo.sendMessage(
                chatId = targetChatId,
                senderId = curr.userId,
                receiverId = targetChat.otherUserId,
                content = message.content,
                type = message.type,
                mediaUrl = message.mediaUrl,
                mediaName = message.mediaName,
                voiceDurationMs = message.voiceDurationMs
            )
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepo.deleteChatConversation(chatId)
            if (activeChat.value?.chatId == chatId) {
                closeActiveChat()
            }
        }
    }

    fun blockUser(otherUserId: String) {
        userRepo.blockUser(otherUserId)
    }

    fun unblockUser(otherUserId: String) {
        userRepo.unblockUser(otherUserId)
    }

    fun reportUser(reportedUserId: String, reason: String, details: String, onDone: () -> Unit) {
        val curr = currentUser.value ?: return
        viewModelScope.launch {
            userRepo.reportUser(curr.userId, reportedUserId, reason, details)
            onDone()
        }
    }

    fun retryFailedMessages() {
        viewModelScope.launch {
            chatRepo.retryFailedMessages()
        }
    }
}
