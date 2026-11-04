<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-12px">
      <el-input v-model="busca" class="!w-320px" clearable placeholder="O que você precisa?">
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Não achou?
        <el-link type="primary" @click="router.push('/meus-chamados')"
          >abra um chamado livre</el-link
        >
      </span>
    </div>
  </ContentWrap>

  <div v-loading="loading">
    <el-empty v-if="!loading && !grupos.length" description="Nenhum serviço encontrado" />
    <ContentWrap v-for="g in grupos" :key="g.nome" :title="g.nome">
      <div class="grade">
        <div
          v-for="f in g.itens"
          :key="f.id"
          class="cartao"
          :class="{ destaque: f.destaque }"
          role="button"
          tabindex="0"
          @click="abrir(f)"
          @keyup.enter="abrir(f)"
        >
          <Icon :icon="icone(f)" :size="26" class="text-[var(--el-color-primary)]" />
          <div class="min-w-0">
            <div class="font-bold">
              <span v-if="f.codigo" class="mr-4px text-[var(--el-text-color-secondary)]">{{
                f.codigo
              }}</span
              >{{ f.nome }}
            </div>
            <div class="descricao">{{ f.descricao }}</div>
            <el-tag
              v-if="f.destaque"
              class="mr-4px mt-6px"
              disable-transitions
              effect="dark"
              size="small"
            >
              destaque
            </el-tag>
            <el-tag
              v-if="f.precisaAprovacao"
              class="mt-6px"
              disable-transitions
              effect="plain"
              size="small"
              type="warning"
            >
              precisa de aprovação
            </el-tag>
          </div>
        </div>
      </div>
    </ContentWrap>
  </div>

  <Dialog v-model="visivel" :title="atual?.nome" :width="larguraDialogo">
    <template v-if="atual">
      <el-alert
        v-if="atual.descricao"
        :closable="false"
        :title="atual.descricao"
        class="mb-12px"
        type="info"
      />
      <el-alert
        v-if="atual.precisaAprovacao"
        :closable="false"
        class="mb-12px"
        show-icon
        title="Este pedido vai para aprovação antes de ser atendido. Você acompanha tudo em Meus chamados."
        type="warning"
      />
      <FormularioRender
        ref="formRef"
        v-model="respostas"
        :definicao="atual.definicao"
        :desabilitado="enviando"
      />
    </template>
    <template #footer>
      <el-button :loading="enviando" type="primary" @click="enviar">Enviar pedido</el-button>
      <el-button @click="visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ItsmApi from '@/api/itsm'
import FormularioRender from '../components/FormularioRender.vue'

defineOptions({ name: 'ItsmCatalogo' })
const message = useMessage()
const router = useRouter()

const loading = ref(true)
const formularios = ref<ItsmApi.FormularioCatalogo[]>([])
const busca = ref('')
const visivel = ref(false)
const atual = ref<ItsmApi.FormularioCatalogo>()
const respostas = ref<Record<string, any>>({})
const formRef = ref()
const enviando = ref(false)
const larguraDialogo = computed(() => (window.innerWidth < 900 ? '100%' : '820px'))

const normal = (s?: string) => (s || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

const grupos = computed(() => {
  const termo = normal(busca.value)
  const mapa = new Map<string, ItsmApi.FormularioCatalogo[]>()
  for (const f of formularios.value) {
    if (termo && !normal(`${f.codigo} ${f.nome} ${f.descricao} ${f.grupo}`).includes(termo))
      continue
    mapa.set(f.grupo, [...(mapa.get(f.grupo) || []), f])
  }
  return [...mapa.entries()].map(([nome, itens]) => ({ nome, itens }))
})

/** Ícone do Iconify ("ep:key") ou, para os nomes do Font Awesome do formato antigo, um equivalente. */
const icone = (f: ItsmApi.FormularioCatalogo) => {
  if (f.icone && f.icone.includes(':')) return f.icone
  const n = normal(`${f.icone} ${f.nome}`)
  const tabela: [string, string][] = [
    ['key', 'ep:key'],
    ['senha', 'ep:key'],
    ['envelope', 'ep:message'],
    ['e-mail', 'ep:message'],
    ['folder', 'ep:folder-opened'],
    ['pasta', 'ep:folder-opened'],
    ['user', 'ep:user'],
    ['usuario', 'ep:user'],
    ['print', 'ep:printer'],
    ['impressora', 'ep:printer'],
    ['toner', 'ep:printer'],
    ['wifi', 'ep:connection'],
    ['internet', 'ep:connection'],
    ['rede', 'ep:connection'],
    ['vpn', 'ep:lock'],
    ['phone', 'ep:phone'],
    ['telefon', 'ep:phone'],
    ['celular', 'ep:iphone'],
    ['software', 'ep:box'],
    ['desktop', 'ep:monitor'],
    ['computador', 'ep:monitor'],
    ['saude', 'ep:first-aid-kit']
  ]
  return tabela.find(([k]) => n.includes(k))?.[1] || 'ep:document'
}

const abrir = (f: ItsmApi.FormularioCatalogo) => {
  atual.value = f
  respostas.value = {}
  visivel.value = true
  nextTick(() => formRef.value?.limpar())
}

const enviar = async () => {
  if (!(await formRef.value.validar())) return
  enviando.value = true
  try {
    const id = await ItsmApi.enviarFormulario(atual.value!.id, respostas.value)
    visivel.value = false
    message.success(`Pedido registrado: chamado #${id}. Acompanhe em Meus chamados.`)
  } finally {
    enviando.value = false
  }
}

onMounted(async () => {
  try {
    formularios.value = await ItsmApi.getCatalogo()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.cartao.destaque {
  background: var(--el-color-primary-light-9);
  border: 2px solid var(--el-color-primary);
  box-shadow: 0 0 12px var(--el-color-primary-light-3);
}

.grade {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.cartao {
  display: flex;
  gap: 12px;
  padding: 14px;
  cursor: pointer;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.cartao:hover,
.cartao:focus-visible {
  border-color: var(--el-color-primary);
  outline: none;
  box-shadow: var(--el-box-shadow-lighter);
}
.descricao {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}
</style>
