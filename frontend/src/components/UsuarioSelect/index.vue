<!-- Campo de pessoa: busca no servidor enquanto se digita (nome ou login) e mostra o nome de quem já está escolhido.
     Aceita os mesmos atributos do el-select (placeholder, clearable, class, size...). -->
<template>
  <el-select
    v-bind="$attrs"
    :model-value="modelValue"
    :multiple="multiple"
    filterable
    remote
    :remote-method="buscar"
    :loading="carregando"
    remote-show-suffix
    @update:model-value="(v) => emit('update:modelValue', v)"
    @visible-change="(aberto: boolean) => aberto && !resultados.length && buscar('')"
  >
    <el-option v-for="u in opcoes" :key="u.id" :label="u.nickname" :value="u.id">
      <span>{{ u.nickname }}</span>
      <span v-if="u.deptName" class="usuario-select__dept">{{ u.deptName }}</span>
    </el-option>
  </el-select>
</template>

<script lang="ts" setup>
import type { UserVO } from '@/api/system/user'
import { buscarUsuarios, garantirUsuarios, usuarioPorId } from '@/hooks/web/useUsuarios'

defineOptions({ name: 'UsuarioSelect', inheritAttrs: false })

const props = defineProps<{
  modelValue?: number | number[] | null
  multiple?: boolean
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()

const resultados = ref<UserVO[]>([])
const carregando = ref(false)
let ultimaBusca = 0

const escolhidos = computed<number[]>(() =>
  props.modelValue == null
    ? []
    : Array.isArray(props.modelValue)
      ? props.modelValue
      : [props.modelValue]
)

// os escolhidos sempre entre as opções, para o campo mostrar o nome e não o número
const opcoes = computed(() => {
  const ids = new Set(resultados.value.map((u) => u.id))
  const extras = escolhidos.value
    .filter((id) => !ids.has(id))
    .map((id) => usuarioPorId(id) || ({ id, nickname: `#${id}` } as UserVO))
  return [...extras, ...resultados.value]
})

watch(escolhidos, (ids) => garantirUsuarios(ids), { immediate: true })

async function buscar(texto: string) {
  const minha = ++ultimaBusca
  carregando.value = true
  try {
    const lista = await buscarUsuarios(texto || '')
    if (minha === ultimaBusca) resultados.value = lista
  } finally {
    if (minha === ultimaBusca) carregando.value = false
  }
}
</script>

<style scoped lang="scss">
.usuario-select__dept {
  float: right;
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
