<!-- Pergunta "usuário de rede" do catálogo: busca a pessoa por nome ou login e guarda o login de rede -->
<template>
  <el-select
    :loading="carregando"
    :model-value="modelValue || undefined"
    :remote-method="buscar"
    class="w-full"
    clearable
    filterable
    placeholder="Digite o nome ou o usuário de rede"
    remote
    remote-show-suffix
    @update:model-value="(v) => emit('update:modelValue', v || '')"
    @visible-change="(aberto: boolean) => aberto && !resultados.length && buscar('')"
  >
    <el-option v-for="u in opcoes" :key="u.username" :label="rotulo(u)" :value="u.username">
      <span>{{ u.nickname }}</span>
      <span class="login-rede__login">{{ u.username }}</span>
      <span v-if="u.deptName" class="login-rede__dept">{{ u.deptName }}</span>
    </el-option>
  </el-select>
</template>

<script lang="ts" setup>
import type { UserVO } from '@/api/system/user'
import { buscarUsuarios } from '@/hooks/web/useUsuarios'

defineOptions({ name: 'LoginRedeSelect' })

const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const resultados = ref<UserVO[]>([])
const escolhido = ref<UserVO>()
const carregando = ref(false)
let ultimaBusca = 0

const rotulo = (u: UserVO) => (u.nickname ? `${u.nickname} (${u.username})` : u.username)

// o já escolhido continua entre as opções (o campo mostra o nome e não só o login)
const opcoes = computed(() => {
  const v = props.modelValue
  if (!v || resultados.value.some((u) => u.username === v)) return resultados.value
  const extra =
    escolhido.value?.username === v ? escolhido.value : ({ username: v, nickname: '' } as UserVO)
  return [extra, ...resultados.value]
})

watch(
  () => props.modelValue,
  (v) => {
    const u = resultados.value.find((x) => x.username === v)
    if (u) escolhido.value = u
  }
)

async function buscar(texto: string) {
  const minha = ++ultimaBusca
  carregando.value = true
  try {
    const lista = (await buscarUsuarios(texto || '')).filter((u) => u.username)
    if (minha === ultimaBusca) resultados.value = lista
  } finally {
    if (minha === ultimaBusca) carregando.value = false
  }
}
</script>

<style scoped lang="scss">
.login-rede__login {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.login-rede__dept {
  float: right;
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
