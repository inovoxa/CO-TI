<template>
  <ContentWrap>
    <el-button plain type="primary" @click="abrir()"
      ><Icon class="mr-5px" icon="ep:plus" /> Nova automação</el-button
    >
    <span class="dica ml-12px"
      >Quando o evento acontece e todas as condições valem, as ações rodam. O que uma automação faz
      não dispara outras automações.</span
    >
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Ativa" width="70">
        <template #default="{ row }">
          <el-switch
            :model-value="row.ativa"
            size="small"
            @change="(v: boolean) => alternar(row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="Automação" min-width="220">
        <template #default="{ row }">
          <div class="font-bold">{{ row.nome }}</div>
          <div v-if="row.descricao" class="dica">{{ row.descricao }}</div>
        </template>
      </el-table-column>
      <el-table-column label="Quando" width="220">
        <template #default="{ row }">{{ EVENTOS[row.evento] }}</template>
      </el-table-column>
      <el-table-column label="Faz" min-width="240">
        <template #default="{ row }">{{
          row.acoes.map((a: Atd.Acao) => ACOES[a.tipo]?.rotulo).join(' · ')
        }}</template>
      </el-table-column>
      <el-table-column align="right" label="Execuções" width="110">
        <template #default="{ row }">
          {{ row.execucoes }}
          <div v-if="row.ultimaExecucao" class="dica">{{ hora(row.ultimaExecucao) }}</div>
        </template>
      </el-table-column>
      <el-table-column width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)"><Icon icon="ep:edit" /></el-button>
          <el-button link type="danger" @click="excluir(row)"><Icon icon="ep:delete" /></el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar automação' : 'Nova automação'"
    width="820px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="110px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Descrição"
        ><el-input v-model="form.dados.descricao" maxlength="500"
      /></el-form-item>
      <el-form-item label="Quando">
        <el-select v-model="form.dados.evento" class="!w-300px">
          <el-option v-for="(r, k) in EVENTOS" :key="k" :label="r" :value="k" />
        </el-select>
        <span class="dica ml-12px">ordem</span>
        <el-input-number
          v-model="form.dados.ordem"
          class="ml-6px"
          :max="999"
          :min="0"
          size="small"
        />
      </el-form-item>
      <el-form-item label="Se">
        <div class="w-full">
          <div v-for="(c, i) in form.dados.condicoes" :key="i" class="linha">
            <el-select v-model="c.campo" class="!w-200px" @change="trocarCampo(c)">
              <el-option v-for="(x, k) in CAMPOS" :key="k" :label="x.rotulo" :value="k" />
            </el-select>
            <el-select v-model="c.operador" class="!w-170px">
              <el-option
                v-for="o in CAMPOS[c.campo]?.operadores"
                :key="o"
                :label="OPERADORES[o]"
                :value="o"
              />
            </el-select>
            <div class="min-w-0 flex-1">
              <template v-if="!SEM_VALOR.includes(c.operador)">
                <el-input
                  v-if="CAMPOS[c.campo]?.valor === 'texto'"
                  v-model="c.valor"
                  maxlength="500"
                  :placeholder="c.campo === 'conteudo' ? 'senha, acesso, e-mail (basta um)' : ''"
                />
                <UsuarioSelect
                  v-else-if="CAMPOS[c.campo]?.valor === 'agentes'"
                  v-model="c.valor"
                  class="w-full"
                  multiple
                />
                <el-select v-else v-model="c.valor" class="w-full" filterable multiple>
                  <el-option
                    v-for="o in opcoes(CAMPOS[c.campo]?.valor)"
                    :key="o.valor"
                    :label="o.rotulo"
                    :value="o.valor"
                  />
                </el-select>
              </template>
            </div>
            <el-button link type="danger" @click="form.dados.condicoes.splice(i, 1)"
              ><Icon icon="ep:delete"
            /></el-button>
          </div>
          <el-button size="small" @click="form.dados.condicoes.push(novaCondicao())"
            ><Icon class="mr-4px" icon="ep:plus" />Condição</el-button
          >
          <span v-if="!form.dados.condicoes.length" class="dica ml-8px">sem condições: sempre</span>
        </div>
      </el-form-item>
      <el-form-item label="Então">
        <AcoesEditor v-model="form.dados.acoes" :etiquetas="etiquetas" />
      </el-form-item>
      <el-form-item label="Ativa"><el-switch v-model="form.dados.ativa" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import {
  ACOES,
  CAMPOS,
  CANAIS,
  EVENTOS,
  OPERADORES,
  PRIORIDADES,
  SEM_VALOR,
  STATUS_CONVERSA
} from '@/api/atendimento'
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import { formatDate } from '@/utils/formatTime'
import AcoesEditor from './AcoesEditor.vue'

defineOptions({ name: 'AtendimentoAutomacao' })
const message = useMessage()
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }

const loading = ref(true)
const lista = ref<Atd.Automacao[]>([])
const caixas = ref<Atd.Caixa[]>([])
const etiquetas = ref<Atd.Etiqueta[]>([])
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Atd.Automacao })
const hora = (t: number) => formatDate(new Date(t), 'DD/MM HH:mm')

const opcoes = (tipo?: string): { valor: any; rotulo: string }[] => {
  switch (tipo) {
    case 'caixas':
      return caixas.value.map((c) => ({ valor: c.id, rotulo: c.nome }))
    case 'canais':
      return Object.entries(CANAIS).map(([k, c]) => ({ valor: k, rotulo: c.rotulo }))
    case 'status':
      return STATUS_CONVERSA.map((s) => ({ valor: s.valor, rotulo: s.rotulo }))
    case 'prioridades':
      return PRIORIDADES.map((p) => ({ valor: p.valor, rotulo: p.rotulo }))
    case 'etiquetas':
      return etiquetas.value.map((e) => ({ valor: e.id, rotulo: e.nome }))
    default:
      return []
  }
}
const novaCondicao = (): Atd.Condicao => ({ campo: 'caixa', operador: 'em', valor: [] })
const trocarCampo = (c: Atd.Condicao) => {
  c.operador = CAMPOS[c.campo].operadores[0]
  c.valor = CAMPOS[c.campo].valor === 'texto' ? '' : []
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Atd.getAutomacoes()
  } finally {
    loading.value = false
  }
}

const abrir = async (a?: Atd.Automacao) => {
  form.dados = a
    ? JSON.parse(JSON.stringify(a))
    : { nome: '', evento: 'mensagem_recebida', condicoes: [], acoes: [], ativa: true, ordem: 0 }
  form.visivel = true
}

const salvar = async () => {
  await formRef.value.validate()
  await Atd.salvarAutomacao(form.dados)
  message.success('Automação salva')
  form.visivel = false
  await carregar()
}

const alternar = async (a: Atd.Automacao, ativa: boolean) => {
  await Atd.salvarAutomacao({ ...a, ativa })
  await carregar()
}

const excluir = async (a: Atd.Automacao) => {
  await message.delConfirm(`Excluir a automação ${a.nome}?`)
  await Atd.excluirAutomacao(a.id!)
  await carregar()
}

onMounted(async () => {
  await carregar()
  ;[caixas.value, etiquetas.value] = await Promise.all([Atd.getCaixas(), Atd.getEtiquetas()])
})
</script>

<style scoped>
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.linha {
  display: flex;
  margin-bottom: 8px;
  align-items: flex-start;
  gap: 8px;
}
</style>
