<template>
  <div class="lateral">
    <template v-if="dados">
      <div class="bloco">
        <div class="text-15px font-bold">{{ dados.contato.nome }}</div>
        <div v-if="dados.contato.telefone" class="info"
          ><Icon icon="ep:phone" :size="12" /> {{ dados.contato.telefone }}</div
        >
        <div v-if="dados.contato.email" class="info"
          ><Icon icon="ep:message" :size="12" /> {{ dados.contato.email }}</div
        >
        <div v-if="dados.contato.usuario" class="info"
          ><Icon icon="ep:user" :size="12" /> Pessoa do portal: {{ dados.contato.usuario }}</div
        >
        <div v-else class="info text-[var(--el-text-color-placeholder)]"
          >Não ligado a uma pessoa do portal</div
        >
        <el-link
          v-if="podeContato"
          :href="`/atendimento/contatos?id=${dados.contato.id}`"
          class="mt-4px"
          target="_blank"
          type="primary"
          >Editar contato</el-link
        >
      </div>

      <div class="bloco">
        <div class="titulo">Etiquetas</div>
        <el-select
          v-model="etiquetaIds"
          class="w-full"
          multiple
          placeholder="Nenhuma"
          size="small"
          @change="salvarEtiquetas"
        >
          <el-option v-for="e in etiquetas" :key="e.id" :label="e.nome" :value="e.id!">
            <span class="cor" :style="{ background: e.cor }"></span>{{ e.nome }}
          </el-option>
        </el-select>
      </div>

      <div class="bloco">
        <div class="titulo">Chamado</div>
        <template v-if="conversa?.chamadoId">
          <el-link
            :href="`/servicedesk/chamados?id=${conversa.chamadoId}`"
            target="_blank"
            type="primary"
            >Chamado #{{ conversa.chamadoId }}</el-link
          >
        </template>
        <el-button v-else-if="podeChamado" size="small" @click="abrirDialogo"
          ><Icon class="mr-4px" icon="ep:tickets" />Abrir chamado</el-button
        >
        <div v-if="dados.chamados.length" class="mt-8px">
          <div class="sub">Chamados do contato</div>
          <div v-for="c in dados.chamados" :key="c.id" class="mini">
            <el-link :href="`/servicedesk/chamados?id=${c.id}`" target="_blank"
              >#{{ c.id }} {{ c.titulo }}</el-link
            >
            <span class="text-[var(--el-text-color-secondary)]"> · {{ c.status }}</span>
          </div>
        </div>
      </div>

      <div v-if="dados.anteriores.length" class="bloco">
        <div class="titulo">Conversas anteriores</div>
        <button
          v-for="c in dados.anteriores"
          :key="c.id"
          class="mini anterior"
          type="button"
          @click="emit('abrir', c.id)"
        >
          #{{ c.id }} · {{ c.statusRotulo }} ·
          {{ formatDate(new Date(c.ultimaAtividadeEm), 'DD/MM/YYYY') }}
          <div class="truncate text-[var(--el-text-color-secondary)]">{{ c.ultimaMensagem }}</div>
        </button>
      </div>
    </template>

    <Dialog v-model="dialogo.visivel" title="Abrir chamado" width="520px">
      <el-form label-position="top">
        <el-form-item label="Título"
          ><el-input v-model="dialogo.titulo" maxlength="255"
        /></el-form-item>
        <el-form-item label="Categoria">
          <el-select v-model="dialogo.categoriaId" class="w-full" clearable filterable>
            <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Urgência">
          <el-select v-model="dialogo.urgencia">
            <el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor" />
          </el-select>
        </el-form-item>
        <el-checkbox v-model="dialogo.incluirConversa"
          >Levar as últimas mensagens para a descrição</el-checkbox
        >
        <el-checkbox v-model="dialogo.avisar">Avisar o contato do número do chamado</el-checkbox>
      </el-form>
      <template #footer>
        <el-button :disabled="!dialogo.titulo.trim()" type="primary" @click="abrirChamado"
          >Abrir</el-button
        >
        <el-button @click="dialogo.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import * as ItsmApi from '@/api/itsm'
import { ESCALA } from '@/api/itsm'
import { formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ConversaLateral' })
const props = defineProps<{ conversa?: Atd.ConversaResumo; etiquetas: Atd.Etiqueta[] }>()
const emit = defineEmits<{ abrir: [id: number]; alterado: [] }>()
const message = useMessage()
const podeChamado = checkPermi(['itsm:chamado:create'])
const podeContato = checkPermi(['atendimento:contato:update'])

const dados = ref<Atd.Lateral>()
const etiquetaIds = ref<number[]>([])
const categorias = ref<ItsmApi.Categoria[]>([])
const dialogo = reactive({
  visivel: false,
  titulo: '',
  categoriaId: undefined as number | undefined,
  urgencia: 3,
  incluirConversa: true,
  avisar: true
})

const carregar = async () => {
  if (!props.conversa) {
    dados.value = undefined
    return
  }
  dados.value = await Atd.getLateral(props.conversa.id)
}
watch(() => props.conversa?.id, carregar, { immediate: true })
watch(
  () => props.conversa?.etiquetas,
  (e) => (etiquetaIds.value = (e || []).map((x) => x.id!)),
  { immediate: true }
)

const salvarEtiquetas = async () => {
  await Atd.definirEtiquetas(props.conversa!.id, etiquetaIds.value)
  emit('alterado')
}

const abrirDialogo = async () => {
  Object.assign(dialogo, {
    visivel: true,
    titulo: props.conversa?.ultimaMensagem?.slice(0, 120) || '',
    categoriaId: undefined,
    urgencia: 3
  })
  if (!categorias.value.length) categorias.value = await ItsmApi.getCategorias(true)
}
const abrirChamado = async () => {
  const id = await Atd.abrirChamado(
    {
      conversaId: props.conversa!.id,
      titulo: dialogo.titulo,
      categoriaId: dialogo.categoriaId,
      urgencia: dialogo.urgencia,
      incluirConversa: dialogo.incluirConversa
    },
    dialogo.avisar
  )
  dialogo.visivel = false
  message.success(`Chamado #${id} aberto`)
  emit('alterado')
  await carregar()
}
defineExpose({ recarregar: carregar })
</script>

<style scoped>
.lateral {
  height: 100%;
  overflow-y: auto;
  border-left: 1px solid var(--el-border-color-lighter);
}
.bloco {
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.titulo {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
}
.sub {
  margin-bottom: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.info {
  display: flex;
  margin-top: 4px;
  font-size: 13px;
  align-items: center;
  gap: 4px;
}
.mini {
  margin-top: 4px;
  font-size: 13px;
}
.anterior {
  display: block;
  width: 100%;
  padding: 4px 0;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}
.anterior:hover {
  color: var(--el-color-primary);
}
.cor {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
}
</style>
