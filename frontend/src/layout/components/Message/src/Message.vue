<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as NotifyMessageApi from '@/api/system/notify/message'
import { useUserStoreWithOut } from '@/store/modules/user'
import { propTypes } from '@/utils/propTypes'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'

defineOptions({ name: 'Message' })

defineProps({
  color: propTypes.string.def('')
})

const { push } = useRouter()
const userStore = useUserStoreWithOut()
const activeName = ref('notice')
const unreadCount = ref(0) // 未读消息数量
const list = ref<any[]>([]) // 消息列表
let unreadCountTimer: ReturnType<typeof setInterval> | undefined

const abertura = ref(false)

// mensagens não lidas; o contador só muda quando a pessoa lê
const getList = async () => {
  list.value = await NotifyMessageApi.getUnreadNotifyMessageList()
}

/** Papel gravado no aviso: técnico abre a fila, aprovador as aprovações e os demais os próprios chamados. */
const destino = (item: any) => {
  const p = item.templateParams || {}
  if (!p.chamado) return null
  if (p.papel === 'tecnico') return { path: '/servicedesk/chamados', query: { id: String(p.chamado) } }
  if (p.papel === 'aprovador') return { path: '/aprovacoes' }
  return { path: '/meus-chamados', query: { chamado: String(p.chamado) } }
}

const abrirAviso = async (item: any) => {
  abertura.value = false
  await NotifyMessageApi.updateNotifyMessageRead([item.id]).catch(() => {})
  list.value = list.value.filter((x) => x.id !== item.id)
  getUnreadCount()
  const d = destino(item)
  if (d) push(d)
}

const lerTodas = async () => {
  await NotifyMessageApi.updateAllNotifyMessageRead()
  list.value = []
  unreadCount.value = 0
}

// 获得未读消息数
const getUnreadCount = async () => {
  NotifyMessageApi.getUnreadNotifyMessageCount().then((data) => {
    unreadCount.value = data
  })
}

// 跳转我的站内信
const goMyList = () => {
  push({
    name: 'MyNotifyMessage'
  })
}

const { on } = useMonitorSocket()
on('sino', () => getUnreadCount())

// ========== 初始化 =========
onMounted(() => {
  // 首次加载小红点
  getUnreadCount()
  // 轮询刷新小红点
  unreadCountTimer = setInterval(
    () => {
      if (userStore.getIsSetUser) {
        getUnreadCount()
      } else {
        unreadCount.value = 0
      }
    },
    1000 * 60 * 2
  )
})

onBeforeUnmount(() => {
  if (unreadCountTimer) {
    clearInterval(unreadCountTimer)
    unreadCountTimer = undefined
  }
})
</script>
<template>
  <div class="message">
    <ElPopover v-model:visible="abertura" :width="400" placement="bottom" trigger="click">
      <template #reference>
        <ElBadge :hidden="!unreadCount" :max="99" :value="unreadCount" class="item">
          <Icon :size="18" class="cursor-pointer" icon="ep:bell" :color="color" @click="getList" />
        </ElBadge>
      </template>
      <ElTabs v-model="activeName">
        <ElTabPane label="Avisos" name="notice">
          <el-empty v-if="!list.length" description="Nenhum aviso novo" :image-size="70" />
          <el-scrollbar v-else class="message-list">
            <template v-for="item in list" :key="item.id">
              <div class="message-item cursor-pointer" role="button" tabindex="0" @click="abrirAviso(item)" @keyup.enter="abrirAviso(item)">
                <img alt="" class="message-icon" src="@/assets/imgs/avatar-padrao.svg" />
                <div class="message-content">
                  <span class="message-title">
                    {{ item.templateNickname }}: {{ item.templateContent }}
                  </span>
                  <span class="message-date">
                    {{ formatDate(item.createTime) }}
                  </span>
                </div>
              </div>
            </template>
          </el-scrollbar>
        </ElTabPane>
      </ElTabs>
      <!-- 更多 -->
      <div style="margin-top: 10px; text-align: right">
        <el-button v-if="list.length" @click="lerTodas">Marcar todos como lidos</el-button>
        <el-button type="primary" @click="goMyList">
          <Icon icon="ep:view" class="mr-1px" /> Ver todas
        </el-button>
      </div>
    </ElPopover>
  </div>
</template>
<style lang="scss" scoped>
// 铃铛对齐修复：
// DOM 链路 div.message > ElPopover > ElBadge(inline-block) > Icon
// 默认 ElBadge 是 inline-block，在父 flex 容器里按 baseline 对齐，导致图标比其他 ElIcon 视觉偏下 1-2px
// 解法：让 .message 本身变 flex-center 容器，且穿透设置内部 .el-badge 也为 flex-center
// 这样从父到子整条链路都走几何中心对齐，不再受 inline-block baseline 影响
.message {
  display: flex;
  align-items: center;
  height: 100%;

  :deep(.el-badge) {
    display: flex;
    align-items: center;
  }
}

.message-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 260px;
  line-height: 45px;
}

.message-list {
  display: flex;
  height: 400px;
  flex-direction: column;

  .message-item {
    display: flex;
    align-items: center;
    padding: 20px 0;
    border-bottom: 1px solid var(--el-border-color-light);

    &:last-child {
      border: none;
    }

    .message-icon {
      width: 40px;
      height: 40px;
      margin: 0 20px 0 5px;
    }

    .message-content {
      display: flex;
      flex-direction: column;

      .message-title {
        margin-bottom: 5px;
      }

      .message-date {
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }
    }
  }
}
</style>
