<template>
  <div>
    <div class="text-center">
      <UserAvatar :img="userInfo?.avatar" />
    </div>
    <ul class="list-group list-group-striped">
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:user" />
        {{ t('profile.user.username') }}
        <div class="pull-right">{{ userInfo?.username }}</div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:phone" />
        {{ t('profile.user.mobile') }}
        <div class="pull-right">{{ userInfo?.mobile }}</div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="fontisto:email" />
        {{ t('profile.user.email') }}
        <div class="pull-right">{{ userInfo?.email }}</div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="carbon:tree-view-alt" />
        {{ t('profile.user.dept') }}
        <div v-if="userInfo?.dept" class="pull-right">{{ userInfo?.dept.name }}</div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:suitcase" />
        {{ t('profile.user.posts') }}
        <div v-if="userInfo?.posts" class="pull-right">
          {{ userInfo?.posts.map((post) => post.name).join(',') }}
        </div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="icon-park-outline:peoples" />
        {{ t('profile.user.roles') }}
        <div v-if="userInfo?.roles" class="pull-right">
          {{ userInfo?.roles.map((role) => role.name).join(',') }}
        </div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:calendar" />
        {{ t('profile.user.createTime') }}
        <div class="pull-right">{{ formatDate(userInfo.createTime) }}</div>
      </li>
      <li class="list-group-item">
        <Icon class="mr-5px" icon="ep:present" />
        Data de nascimento
        <div v-if="!editandoNascimento" class="pull-right">
          <template v-if="nascimento.data">
            {{ dataBr(nascimento.data) }}
            <el-tag :type="nascimento.publico ? 'success' : 'info'" class="ml-4px" size="small">
              {{ nascimento.publico ? 'Pública' : 'Privada' }}
            </el-tag>
          </template>
          <span v-else class="ajuda">Não informada</span>
          <el-link class="ml-6px" type="primary" @click="editarNascimento">
            <Icon icon="ep:edit" />
          </el-link>
        </div>
        <div v-else class="nascimento-edicao">
          <el-date-picker
            v-model="formNascimento.data"
            :disabled-date="(d: Date) => d.getTime() > Date.now()"
            class="!w-full"
            format="DD/MM/YYYY"
            placeholder="dd/mm/aaaa"
            type="date"
            value-format="YYYY-MM-DD"
          />
          <el-radio-group v-model="formNascimento.publico" class="mt-6px">
            <el-radio :value="true">Pública no calendário</el-radio>
            <el-radio :value="false">Privada</el-radio>
          </el-radio-group>
          <p class="ajuda">
            Pública: seu nome aparece nos aniversariantes do Calendário, só com dia e mês (o ano não aparece para ninguém).
            Privada: só você vê.
          </p>
          <div class="text-right">
            <el-button size="small" @click="editandoNascimento = false">Cancelar</el-button>
            <el-button :loading="salvandoNascimento" size="small" type="primary" @click="salvarNascimento">Salvar</el-button>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import UserAvatar from './UserAvatar.vue'
import { useUserStore } from '@/store/modules/user'

import { getUserProfile, ProfileVO } from '@/api/system/user/profile'
import * as CalendarioApi from '@/api/calendario'

defineOptions({ name: 'ProfileUser' })

const { t } = useI18n()
const message = useMessage()
const userStore = useUserStore()
const userInfo = ref({} as ProfileVO)

const nascimento = ref<CalendarioApi.Nascimento>({ data: null, publico: false })
const formNascimento = reactive<CalendarioApi.Nascimento>({ data: null, publico: false })
const editandoNascimento = ref(false)
const salvandoNascimento = ref(false)

const dataBr = (iso: string) => iso.split('-').reverse().join('/')

const editarNascimento = () => {
  formNascimento.data = nascimento.value.data
  formNascimento.publico = nascimento.value.publico
  editandoNascimento.value = true
}

const salvarNascimento = async () => {
  salvandoNascimento.value = true
  try {
    await CalendarioApi.salvarMeuNascimento({ data: formNascimento.data || null, publico: formNascimento.publico })
    nascimento.value = { data: formNascimento.data || null, publico: formNascimento.publico }
    editandoNascimento.value = false
    message.success('Data de nascimento salva')
  } finally {
    salvandoNascimento.value = false
  }
}

const getUserInfo = async () => {
  const [users, nasc] = await Promise.all([getUserProfile(), CalendarioApi.getMeuNascimento().catch(() => null)])
  userInfo.value = users
  if (nasc) nascimento.value = nasc
}

// 监听 userStore 中头像的变化，同步更新本地 userInfo
watch(
  () => userStore.getUser.avatar,
  (newAvatar) => {
    if (newAvatar && userInfo.value) {
      userInfo.value.avatar = newAvatar
    }
  }
)

// 暴露刷新方法
defineExpose({
  refresh: getUserInfo
})

onMounted(async () => {
  await getUserInfo()
})
</script>

<style scoped>
.text-center {
  position: relative;
  height: 120px;
  text-align: center;
}

.list-group-striped > .list-group-item {
  padding-right: 0;
  padding-left: 0;
  border-right: 0;
  border-left: 0;
  border-radius: 0;
}

.list-group {
  padding-left: 0;
  list-style: none;
}

.list-group-item {
  padding: 11px 0;
  margin-bottom: -1px;
  font-size: 13px;
  border-top: 1px solid #e7eaec;
  border-bottom: 1px solid #e7eaec;
}

.pull-right {
  float: right !important;
}

.nascimento-edicao {
  margin-top: 8px;
}

.ajuda {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
