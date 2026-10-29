<template>
  <el-drawer v-model="visivel" :size="largura" destroy-on-close @closed="chamado = undefined">
    <template #header>
      <div v-if="chamado" class="min-w-0">
        <div class="truncate text-17px font-bold">#{{ chamado.id }} · {{ chamado.titulo }}</div>
        <div class="mt-6px flex flex-wrap items-center gap-6px">
          <el-tag :type="corStatus(chamado.status)" disable-transitions>{{
            chamado.statusRotulo
          }}</el-tag>
          <el-tag :type="corPrioridade(chamado.prioridade)" disable-transitions effect="plain">
            Prioridade {{ chamado.prioridadeRotulo.toLowerCase() }}
          </el-tag>
          <el-tag disable-transitions effect="plain" type="info">{{ chamado.tipoRotulo }}</el-tag>
          <el-tag v-if="chamado.slaVencido" disable-transitions effect="dark" type="danger"
            >Prazo vencido</el-tag
          >
          <el-tag
            v-if="chamado.validacao && chamado.validacao > 1"
            :type="corAprovacao(chamado.validacao)"
            disable-transitions
          >
            {{ chamado.validacaoRotulo }}
          </el-tag>
        </div>
      </div>
    </template>

    <div v-if="chamado" v-loading="carregando" class="flex gap-16px">
      <!-- coluna principal -->
      <div class="min-w-0 flex-1">
        <!-- ações -->
        <div class="mb-12px flex flex-wrap gap-8px">
          <el-button
            v-if="pode('assumir')"
            type="primary"
            @click="acao(() => ItsmApi.assumir(chamado!.id))"
          >
            <Icon class="mr-4px" icon="ep:pointer" />Assumir
          </el-button>
          <el-button v-if="pode('pendente')" @click="pendente">
            <Icon class="mr-4px" icon="ep:video-pause" />Pendente
          </el-button>
          <el-button v-if="pode('retomar')" @click="acao(() => ItsmApi.retomar(chamado!.id))">
            <Icon class="mr-4px" icon="ep:video-play" />Retomar
          </el-button>
          <el-button v-if="pode('fechar')" @click="fechar"
            ><Icon class="mr-4px" icon="ep:circle-check" />Fechar</el-button
          >
          <el-button
            v-if="pode('reabrir')"
            @click="acao(() => ItsmApi.reabrir(chamado!.id), 'Reabrir o chamado?')"
          >
            <Icon class="mr-4px" icon="ep:refresh-left" />Reabrir
          </el-button>
          <el-button v-if="podeAtender && chamado.status !== 6" @click="abrirKb">
            <Icon class="mr-4px" icon="ep:reading" />Base de conhecimento
          </el-button>
          <el-button v-if="escreveKb && [5, 6].includes(chamado.status)" @click="salvarNaBase">
            <Icon class="mr-4px" icon="ep:document-add" />Salvar solução na base
          </el-button>
          <el-button v-if="pode('pedir-aprovacao')" @click="abrirPedidoAprovacao">
            <Icon class="mr-4px" icon="ep:select" />Pedir aprovação
          </el-button>
          <el-button v-if="pode('excluir')" plain type="danger" @click="excluir">
            <Icon class="mr-4px" icon="ep:delete" />Excluir
          </el-button>
        </div>

        <el-alert
          v-if="pode('avaliar-solucao')"
          :closable="false"
          class="mb-12px"
          show-icon
          title="Solução aguardando aprovação do solicitante"
          type="success"
        >
          <template #default>
            <el-button size="small" type="success" @click="avaliar(true)"
              >Aprovar em nome dele (fecha)</el-button
            >
            <el-button size="small" @click="avaliar(false)">Recusar (reabre)</el-button>
          </template>
        </el-alert>

        <el-alert
          v-if="chamado.execucaoAdStatus"
          :closable="false"
          :type="corExecucao"
          class="mb-12px"
          show-icon
        >
          <template #title>
            Automação no AD ({{ rotuloAcaoAd }}):
            {{ STATUS_EXECUCAO[chamado.execucaoAdStatus]?.rotulo || chamado.execucaoAdStatus }}
          </template>
          <span v-if="chamado.execucaoAdStatus === 'erro'"
            >Veja o motivo e repita ou cancele em Service Desk → Execuções no AD.</span
          >
        </el-alert>
        <PainelAprovacoes
          :itens="aprovacoes"
          :rotulo="chamado.validacaoRotulo"
          :validacao="chamado.validacao"
        />

        <el-tabs v-model="aba">
          <el-tab-pane label="Linha do tempo" name="tempo">
            <div class="mb-12px" v-html="chamado.descricao || '<i>Sem descrição</i>'"></div>
            <el-divider class="!my-12px" />
            <LinhaDoTempo :itens="linha" />

            <!-- nova interação -->
            <el-card v-if="tiposPermitidos.length" class="mt-12px" shadow="never">
              <el-radio-group v-model="nova.tipo" class="mb-10px" size="small">
                <el-radio-button v-for="t in tiposPermitidos" :key="t.valor" :value="t.valor">{{
                  t.rotulo
                }}</el-radio-button>
              </el-radio-group>
              <Editor
                :key="editorKey"
                v-model="nova.conteudo"
                directory="chamados"
                height="180px"
              />
              <div class="mt-10px flex flex-wrap items-center gap-12px">
                <el-checkbox v-if="nova.tipo !== 'solucao'" v-model="nova.privado"
                  >Privado (só a equipe técnica vê)</el-checkbox
                >
                <template v-if="nova.tipo === 'tarefa'">
                  <el-input-number
                    v-model="nova.tempoGastoMin"
                    :max="100000"
                    :min="0"
                    controls-position="right"
                    size="small"
                  />
                  <span class="text-13px">min gastos</span>
                  <el-select v-model="nova.estadoTarefa" class="!w-140px" size="small">
                    <el-option :value="1" label="A fazer" />
                    <el-option :value="2" label="Feita" />
                    <el-option :value="0" label="Informativa" />
                  </el-select>
                  <el-date-picker
                    v-model="nova.periodo"
                    end-placeholder="Fim"
                    size="small"
                    start-placeholder="Planejar início"
                    type="datetimerange"
                    value-format="YYYY-MM-DD HH:mm:ss"
                  />
                </template>
                <el-button :loading="enviando" class="ml-auto" type="primary" @click="enviar">
                  {{ nova.tipo === 'solucao' ? 'Registrar solução' : 'Enviar' }}
                </el-button>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane v-if="pode('editar')" label="Dados" name="dados">
            <el-form :model="edicao" label-width="100px">
              <el-form-item label="Título"
                ><el-input v-model="edicao.titulo" maxlength="255"
              /></el-form-item>
              <el-form-item label="Tipo">
                <el-radio-group v-model="edicao.tipo">
                  <el-radio-button :value="1">Incidente</el-radio-button>
                  <el-radio-button :value="2">Requisição</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="Categoria">
                <el-select v-model="edicao.categoriaId" class="w-full" clearable filterable>
                  <el-option
                    v-for="c in categorias"
                    :key="c.id"
                    :label="c.nomeCompleto"
                    :value="c.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Urgência">
                <el-select v-model="edicao.urgencia">
                  <el-option
                    v-for="e in ESCALA"
                    :key="e.valor"
                    :label="e.rotulo"
                    :value="e.valor"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Impacto">
                <el-select v-model="edicao.impacto">
                  <el-option
                    v-for="e in ESCALA"
                    :key="e.valor"
                    :label="e.rotulo"
                    :value="e.valor"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Prioridade">
                <el-select
                  v-model="edicao.prioridade"
                  clearable
                  placeholder="Recalcular pela matriz"
                >
                  <el-option
                    v-for="p in PRIORIDADES"
                    :key="p.valor"
                    :label="p.rotulo"
                    :value="p.valor"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Descrição">
                <Editor v-model="edicao.descricao" directory="chamados" height="220px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="salvarEdicao">Salvar</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="Histórico" lazy name="historico">
            <el-table :data="historico" size="small">
              <el-table-column
                :formatter="dateFormatter"
                label="Quando"
                prop="momento"
                width="160"
              />
              <el-table-column label="Quem" prop="usuario" width="150" />
              <el-table-column label="O quê" width="140">
                <template #default="{ row }">{{ rotuloCampo(row.campo) }}</template>
              </el-table-column>
              <el-table-column label="De" prop="valorAntigo" show-overflow-tooltip />
              <el-table-column label="Para" prop="valorNovo" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- coluna lateral -->
      <div class="w-300px shrink-0">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Categoria">{{
            chamado.categoria || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="Unidade">{{ chamado.dept || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Origem">
            {{ chamado.origem || '—'
            }}<span v-if="chamado.origemAutomatica"> ({{ chamado.origemAutomatica }})</span>
          </el-descriptions-item>
          <el-descriptions-item label="Aberto em">{{
            formatDate(chamado.abertoEm)
          }}</el-descriptions-item>
          <el-descriptions-item label="Assumido">
            <template v-if="chamado.assumidoEm"
              >{{ formatDate(chamado.assumidoEm) }} ({{
                duracao(chamado.tempoAssumirSeg)
              }})</template
            >
            <template v-else>—</template>
          </el-descriptions-item>
          <el-descriptions-item label="Assumir até">
            <template v-if="chamado.prazoAtendimento">
              <span
                :class="{ 'text-[var(--el-color-danger)] font-bold': chamado.atendimentoVencido }"
              >
                {{ formatDate(chamado.prazoAtendimento) }}
              </span>
              <div class="text-12px text-[var(--el-text-color-secondary)]">{{
                chamado.slaAtendimento
              }}</div>
            </template>
            <template v-else>sem SLA</template>
          </el-descriptions-item>
          <el-descriptions-item label="Solucionar até">
            <template v-if="chamado.prazoSolucao">
              <span :class="{ 'text-[var(--el-color-danger)] font-bold': chamado.slaVencido }">
                {{ formatDate(chamado.prazoSolucao) }}
              </span>
              <el-tag
                v-if="chamado.status === 4"
                class="ml-4px"
                disable-transitions
                size="small"
                type="warning"
                >pausado</el-tag
              >
              <div class="text-12px text-[var(--el-text-color-secondary)]">{{
                chamado.slaSolucao
              }}</div>
            </template>
            <template v-else>sem SLA</template>
          </el-descriptions-item>
          <el-descriptions-item v-if="chamado.solucionadoEm" label="Solucionado">
            {{ formatDate(chamado.solucionadoEm) }} ({{ duracao(chamado.tempoSolucaoSeg) }})
          </el-descriptions-item>
          <el-descriptions-item v-if="chamado.fechadoEm" label="Fechado">{{
            formatDate(chamado.fechadoEm)
          }}</el-descriptions-item>
          <el-descriptions-item label="Pendente">{{
            duracao(chamado.esperaSeg)
          }}</el-descriptions-item>
          <el-descriptions-item label="Tempo gasto">{{
            duracao(chamado.tempoGastoSeg)
          }}</el-descriptions-item>
          <el-descriptions-item
            v-if="chamado.satisfacaoNota || chamado.satisfacaoPendente"
            label="Satisfação"
          >
            <template v-if="chamado.satisfacaoNota">
              <el-rate :model-value="chamado.satisfacaoNota" disabled />
              <div v-if="chamado.satisfacaoComentario" class="text-12px"
                >“{{ chamado.satisfacaoComentario }}”</div
              >
            </template>
            <template v-else>aguardando resposta</template>
          </el-descriptions-item>
        </el-descriptions>

        <div v-for="g in GRUPOS" :key="g.tipo" class="mt-14px">
          <div class="mb-6px flex items-center justify-between text-13px font-bold">
            {{ g.rotulo }}
            <el-button
              v-if="pode('atribuir')"
              link
              size="small"
              type="primary"
              @click="abrirAtor(g.tipo)"
            >
              <Icon icon="ep:plus" />
            </el-button>
          </div>
          <div class="flex flex-wrap gap-6px">
            <el-tag
              v-for="a in (chamado as any)[g.campo]"
              :key="a.id"
              :closable="pode('atribuir')"
              disable-transitions
              effect="plain"
              @close="removerAtor(a)"
            >
              <Icon :icon="a.equipeId ? 'ep:user-filled' : 'ep:user'" class="mr-2px" />{{ a.nome }}
            </el-tag>
            <span
              v-if="!(chamado as any)[g.campo].length"
              class="text-12px text-[var(--el-text-color-placeholder)]"
              >ninguém</span
            >
          </div>
        </div>

        <div v-if="veItil" class="mt-14px">
          <div class="mb-6px flex items-center justify-between text-13px font-bold">
            Problemas e mudanças
            <el-dropdown v-if="criaProblema || criaMudanca" trigger="click" @command="acaoItil">
              <el-button link size="small" type="primary"><Icon icon="ep:plus" /></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="criaProblema" command="novo:problema"
                    >Criar problema a partir deste chamado</el-dropdown-item
                  >
                  <el-dropdown-item v-if="criaMudanca" command="novo:mudanca"
                    >Criar mudança a partir deste chamado</el-dropdown-item
                  >
                  <el-dropdown-item v-if="criaProblema" command="vincular:problema" divided
                    >Vincular a um problema existente</el-dropdown-item
                  >
                  <el-dropdown-item v-if="criaMudanca" command="vincular:mudanca"
                    >Vincular a uma mudança existente</el-dropdown-item
                  >
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div v-for="v in itil" :key="v.id" class="mb-4px text-13px">
            <el-link
              :href="`/servicedesk/${v.objeto === 'problema' ? 'problemas' : 'mudancas'}?id=${v.objetoId}`"
              target="_blank"
              type="primary"
              >{{ v.objeto === 'problema' ? 'Problema' : 'Mudança' }} #{{ v.objetoId }}</el-link
            >
            {{ v.titulo }}
            <span class="text-[var(--el-text-color-secondary)]">· {{ v.statusRotulo }}</span>
          </div>
          <span v-if="!itil.length" class="text-12px text-[var(--el-text-color-placeholder)]"
            >nenhum</span
          >
        </div>

        <div v-if="veInventario" class="mt-14px">
          <div class="mb-6px flex items-center justify-between text-13px font-bold">
            Equipamentos
            <el-button
              v-if="podeEditar && !equip.escolhendo"
              link
              size="small"
              type="primary"
              @click="escolherEquipamento"
              ><Icon icon="ep:plus"
            /></el-button>
          </div>
          <div v-if="equip.escolhendo" class="mb-6px flex gap-4px">
            <el-select
              v-model="equip.escolhido"
              :loading="equip.buscando"
              :remote-method="buscarEquipamentos"
              class="flex-1"
              filterable
              placeholder="Nome, patrimônio ou IP"
              remote
              size="small"
            >
              <el-option
                v-for="o in equip.opcoes"
                :key="o.id"
                :label="o.patrimonio ? `${o.nome} (${o.patrimonio})` : o.nome"
                :value="o.id"
              />
            </el-select>
            <el-button
              :disabled="!equip.escolhido"
              size="small"
              type="primary"
              @click="ligarEquipamento"
              >Ligar</el-button
            >
            <el-button size="small" @click="equip.escolhendo = false">Cancelar</el-button>
          </div>
          <div v-for="e in equip.lista" :key="e.id" class="mb-4px flex items-center text-13px">
            <el-link :href="`/inventario/ativos?id=${e.id}`" target="_blank" type="primary">{{
              e.nome
            }}</el-link>
            <span class="ml-4px truncate text-[var(--el-text-color-secondary)]"
              >· {{ e.usuarioNome || e.usuarioLogin || e.soNome || e.tipo }}</span
            >
            <el-button
              v-if="podeEditar"
              class="ml-auto"
              link
              size="small"
              type="danger"
              @click="desligarEquipamento(e)"
              ><Icon icon="ep:close"
            /></el-button>
          </div>
          <span v-if="!equip.lista.length" class="text-12px text-[var(--el-text-color-placeholder)]"
            >nenhum</span
          >
        </div>
      </div>
    </div>

    <!-- base de conhecimento -->
    <Dialog v-model="kb.visivel" title="Base de conhecimento" width="760px">
      <el-input v-model="kb.q" clearable placeholder="Buscar" @input="buscarKbDepois">
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <div v-if="kb.usados.length" class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
        Já usados neste chamado: {{ kb.usados.map((a) => a.titulo).join(', ') }}
      </div>
      <el-empty v-if="!kb.lista.length" :image-size="60" description="Nada encontrado" />
      <div v-for="a in kb.lista" :key="a.id" class="kb-item">
        <div class="font-bold"
          >{{ a.titulo }}
          <el-tag v-if="!a.faq" disable-transitions size="small">interno</el-tag></div
        >
        <!-- trecho escapado no servidor -->
        <div class="text-13px" v-html="a.trecho"></div>
        <div class="mt-6px flex gap-8px">
          <el-button v-if="pode('solucionar')" size="small" type="success" @click="usarKb(a, true)"
            >Usar como solução</el-button
          >
          <el-button size="small" @click="usarKb(a, false)">Enviar como acompanhamento</el-button>
          <el-link :href="`/base-conhecimento?artigo=${a.id}`" target="_blank" type="primary"
            >abrir</el-link
          >
        </div>
      </div>
    </Dialog>

    <!-- pedir aprovação -->
    <Dialog v-model="pedido.visivel" title="Pedir aprovação" width="460px">
      <el-form label-position="top">
        <el-form-item label="Quem aprova (todos precisam aprovar)">
          <UsuarioSelect
            v-model="pedido.aprovadorIds"
            class="w-full"
            filterable
            multiple
            placeholder="Pessoas"
          />
        </el-form-item>
        <el-form-item label="O que precisa ser aprovado">
          <el-input v-model="pedido.comentario" maxlength="2000" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          :disabled="!pedido.aprovadorIds.length"
          type="primary"
          @click="enviarPedidoAprovacao"
          >Pedir</el-button
        >
        <el-button @click="pedido.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>

    <!-- incluir participante -->
    <Dialog
      v-model="atorDialogo.visivel"
      :title="`Incluir ${atorDialogo.rotulo.toLowerCase()}`"
      width="420px"
    >
      <el-radio-group v-if="atorDialogo.tipo === 2" v-model="atorDialogo.modo" class="mb-10px">
        <el-radio-button value="pessoa">Pessoa</el-radio-button>
        <el-radio-button value="equipe">Equipe</el-radio-button>
      </el-radio-group>
      <UsuarioSelect
        v-if="atorDialogo.modo === 'pessoa'"
        v-model="atorDialogo.userId"
        class="w-full"
        filterable
        placeholder="Pessoa"
      />
      <el-select
        v-else
        v-model="atorDialogo.equipeId"
        class="w-full"
        filterable
        placeholder="Equipe"
      >
        <el-option v-for="e in equipes" :key="e.id" :label="e.nome" :value="e.id!" />
      </el-select>
      <template #footer>
        <el-button type="primary" @click="incluirAtor">Incluir</el-button>
        <el-button @click="atorDialogo.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>
  </el-drawer>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import { ElMessageBox } from 'element-plus'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as ItsmApi from '@/api/itsm'
import {
  ACOES_AD,
  ESCALA,
  PRIORIDADES,
  STATUS_EXECUCAO,
  corAprovacao,
  corPrioridade,
  corStatus,
  duracao
} from '@/api/itsm'
import * as InvApi from '@/api/inventario'
import { checkPermi } from '@/utils/permission'
import LinhaDoTempo from '../components/LinhaDoTempo.vue'
import PainelAprovacoes from '../components/PainelAprovacoes.vue'

defineOptions({ name: 'ChamadoDetalhe' })
const emit = defineEmits(['alterado'])
const message = useMessage()

const GRUPOS = [
  { tipo: 1, rotulo: 'Solicitantes', campo: 'solicitantes' },
  { tipo: 2, rotulo: 'Técnicos', campo: 'tecnicos' },
  { tipo: 3, rotulo: 'Observadores', campo: 'observadores' }
]
const CAMPOS: Record<string, string> = {
  criado: 'Abertura',
  status: 'Situação',
  titulo: 'Título',
  tipo: 'Tipo',
  urgencia: 'Urgência',
  impacto: 'Impacto',
  prioridade: 'Prioridade',
  categoria: 'Categoria',
  unidade: 'Unidade',
  descricao: 'Descrição',
  'solicitante+': '+ Solicitante',
  'solicitante-': '− Solicitante',
  'tecnico+': '+ Técnico',
  'tecnico-': '− Técnico',
  'observador+': '+ Observador',
  'observador-': '− Observador',
  excluido: 'Exclusão',
  sla: 'SLA',
  escalonamento: 'Escalonamento',
  satisfacao: 'Satisfação',
  regra: 'Regra aplicada',
  'aprovacao+': '+ Aprovador',
  'problema+': '+ Problema',
  'mudanca+': '+ Mudança',
  aprovacao: 'Aprovação',
  ativo: 'Equipamento'
}
const rotuloCampo = (c: string) => CAMPOS[c] || c

const visivel = ref(false)
const carregando = ref(false)
const enviando = ref(false)
const aba = ref('tempo')
const chamado = ref<ItsmApi.Chamado>()
const linha = ref<ItsmApi.Interacao[]>([])
const aprovacoes = ref<ItsmApi.Aprovacao[]>([])
const historico = ref<ItsmApi.Historico[]>([])
const categorias = ref<ItsmApi.Categoria[]>([])
const equipes = ref<ItsmApi.Equipe[]>([])
const edicao = ref<any>({})
const editorKey = ref(0)
const largura = computed(() => (window.innerWidth < 1200 ? '100%' : '75%'))

const novaVazia = () => ({
  tipo: 'acompanhamento',
  conteudo: '',
  privado: false,
  tempoGastoMin: 0,
  estadoTarefa: 2,
  periodo: [] as string[]
})
const nova = ref(novaVazia())
const atorDialogo = reactive({
  visivel: false,
  tipo: 1,
  rotulo: '',
  modo: 'pessoa',
  userId: undefined as number | undefined,
  equipeId: undefined as number | undefined
})

const pode = (a: string) => !!chamado.value?.acoes.includes(a)
const rotuloAcaoAd = computed(
  () =>
    ACOES_AD.find((x) => x.valor === chamado.value?.execucaoAdAcao)?.rotulo ||
    chamado.value?.execucaoAdAcao
)
const corExecucao = computed(() => {
  const c = STATUS_EXECUCAO[chamado.value?.execucaoAdStatus || '']?.cor
  return (c === 'danger' ? 'error' : c === 'primary' ? 'info' : c || 'info') as any
})
const tiposPermitidos = computed(() =>
  [
    { valor: 'acompanhamento', rotulo: 'Acompanhamento', acao: 'acompanhar' },
    { valor: 'tarefa', rotulo: 'Tarefa', acao: 'tarefa' },
    { valor: 'solucao', rotulo: 'Solução', acao: 'solucionar' }
  ].filter((t) => pode(t.acao))
)

const carregar = async (id: number) => {
  carregando.value = true
  try {
    const [c, l, a] = await Promise.all([
      ItsmApi.getChamado(id),
      ItsmApi.getLinhaDoTempo(id),
      ItsmApi.getAprovacoesDoChamado(id)
    ])
    chamado.value = c
    linha.value = l
    aprovacoes.value = a
    edicao.value = {
      id: c.id,
      titulo: c.titulo,
      tipo: c.tipo,
      categoriaId: c.categoriaId,
      urgencia: c.urgencia,
      impacto: c.impacto,
      prioridade: c.prioridade,
      descricao: c.descricao
    }
    if (aba.value === 'historico') historico.value = await ItsmApi.getHistorico(id)
    itil.value = veItil ? await ItsmApi.getItilDoChamado(id) : []
    equip.lista = veInventario ? await InvApi.getAtivosDoChamado(id) : []
    equip.escolhendo = false
  } finally {
    carregando.value = false
  }
}

const open = async (id: number) => {
  visivel.value = true
  aba.value = 'tempo'
  nova.value = novaVazia()
  await carregar(id)
  ;[categorias.value, equipes.value] = await Promise.all([
    ItsmApi.getCategorias(true),
    ItsmApi.getEquipes(true)
  ])
}
defineExpose({ open })

watch(aba, async (a) => {
  if (a === 'historico' && chamado.value)
    historico.value = await ItsmApi.getHistorico(chamado.value.id)
})

const recarregar = async () => {
  await carregar(chamado.value!.id)
  emit('alterado')
}

const acao = async (fn: () => Promise<any>, confirmar?: string) => {
  if (confirmar) await message.confirm(confirmar)
  await fn()
  await recarregar()
}

const pendente = async () => {
  const { value } = await ElMessageBox.prompt(
    'Motivo (vai para a linha do tempo, visível ao solicitante)',
    'Colocar em pendente',
    {
      inputPlaceholder: 'Ex.: aguardando o fornecedor',
      confirmButtonText: 'Colocar pendente',
      cancelButtonText: 'Cancelar'
    }
  )
  await ItsmApi.colocarPendente(chamado.value!.id, value)
  await recarregar()
}

const fechar = () =>
  acao(
    () => ItsmApi.fechar(chamado.value!.id),
    'Fechar o chamado sem esperar a aprovação do solicitante?'
  )

const excluir = async () => {
  await message.delConfirm('Excluir o chamado #' + chamado.value!.id + '?')
  await ItsmApi.excluirChamado(chamado.value!.id)
  visivel.value = false
  emit('alterado')
}

const avaliar = async (aprovar: boolean) => {
  let comentario: string | undefined
  if (!aprovar) {
    comentario = (
      await ElMessageBox.prompt('Por que a solução foi recusada?', 'Recusar solução', {
        confirmButtonText: 'Recusar e reabrir',
        cancelButtonText: 'Cancelar'
      })
    ).value
  }
  await ItsmApi.avaliarSolucaoTecnico({ chamadoId: chamado.value!.id, aprovar, comentario })
  await recarregar()
}

const enviar = async () => {
  if (!nova.value.conteudo || nova.value.conteudo === '<p><br></p>') {
    message.warning('Escreva o texto')
    return
  }
  enviando.value = true
  try {
    const n = nova.value
    await ItsmApi.adicionarInteracao({
      chamadoId: chamado.value!.id,
      tipo: n.tipo,
      conteudo: n.conteudo,
      privado: n.tipo !== 'solucao' && n.privado,
      tempoGastoSeg: n.tipo === 'tarefa' ? n.tempoGastoMin * 60 : undefined,
      estadoTarefa: n.tipo === 'tarefa' ? (n.periodo?.length ? 1 : n.estadoTarefa) : undefined,
      inicio: n.tipo === 'tarefa' && n.periodo?.length ? n.periodo[0] : undefined,
      fim: n.tipo === 'tarefa' && n.periodo?.length ? n.periodo[1] : undefined
    })
    nova.value = novaVazia()
    editorKey.value++
    await recarregar()
  } finally {
    enviando.value = false
  }
}

const salvarEdicao = async () => {
  await ItsmApi.atualizarChamado(edicao.value)
  message.success('Alterações salvas')
  await recarregar()
}

const abrirAtor = (tipo: number) => {
  Object.assign(atorDialogo, {
    visivel: true,
    tipo,
    rotulo: GRUPOS.find((g) => g.tipo === tipo)!.rotulo.slice(0, -1),
    modo: 'pessoa',
    userId: undefined,
    equipeId: undefined
  })
}

const incluirAtor = async () => {
  await ItsmApi.adicionarAtor({
    chamadoId: chamado.value!.id,
    tipo: atorDialogo.tipo,
    userId: atorDialogo.modo === 'pessoa' ? atorDialogo.userId : undefined,
    equipeId: atorDialogo.modo === 'equipe' ? atorDialogo.equipeId : undefined
  })
  atorDialogo.visivel = false
  await recarregar()
}

// ---------- equipamentos (inventário) ----------
const veInventario = checkPermi(['inventario:ativo:query'])
const podeEditar = checkPermi(['itsm:chamado:update'])
const equip = reactive({
  lista: [] as InvApi.Ativo[],
  escolhendo: false,
  escolhido: undefined as number | undefined,
  opcoes: [] as InvApi.Ativo[],
  buscando: false
})
/** Começa pelos equipamentos de quem pediu; a busca procura em todos */
const escolherEquipamento = async () => {
  equip.escolhendo = true
  equip.escolhido = undefined
  const ids = (chamado.value?.solicitantes ?? [])
    .map((s) => s.userId)
    .filter((x): x is number => !!x)
  const listas = await Promise.all(ids.map((u) => InvApi.getAtivosDoUsuario(u)))
  const ligados = new Set(equip.lista.map((e) => e.id))
  equip.opcoes = listas.flat().filter((e) => !ligados.has(e.id))
}
const buscarEquipamentos = async (texto: string) => {
  if (!texto) return
  equip.buscando = true
  try {
    const r = await InvApi.getAtivos({ busca: texto, pageNo: 1, pageSize: 20 })
    equip.opcoes = r.list
  } finally {
    equip.buscando = false
  }
}
const ligarEquipamento = async () => {
  await InvApi.vincularAtivo(chamado.value!.id, equip.escolhido!)
  await recarregar()
}
const desligarEquipamento = async (e: InvApi.Ativo) => {
  await message.confirm(`Desligar ${e.nome} deste chamado?`)
  await InvApi.desvincularAtivo(chamado.value!.id, e.id)
  await recarregar()
}

const veItil = checkPermi(['itsm:problema:query', 'itsm:mudanca:query'])
const criaProblema = checkPermi(['itsm:problema:update'])
const criaMudanca = checkPermi(['itsm:mudanca:update'])
const itil = ref<ItsmApi.VinculoItil[]>([])
const acaoItil = async (comando: string) => {
  const [acaoItem, objeto] = comando.split(':') as [string, ItsmApi.ObjetoItil]
  const nome = objeto === 'problema' ? 'problema' : 'mudança'
  if (acaoItem === 'novo') {
    const id = await ItsmApi.criarItilDoChamado(objeto, chamado.value!.id)
    message.success(
      `${objeto === 'problema' ? 'Problema' : 'Mudança'} #${id} registrado e vinculado`
    )
    window.open(
      `/servicedesk/${objeto === 'problema' ? 'problemas' : 'mudancas'}?id=${id}`,
      '_blank'
    )
  } else {
    const { value } = await ElMessageBox.prompt(
      `Número do ${nome}`,
      `Vincular a ${nome} existente`,
      {
        inputPattern: /^#?\d+$/,
        inputErrorMessage: 'Informe o número',
        confirmButtonText: 'Vincular',
        cancelButtonText: 'Cancelar'
      }
    )
    await ItsmApi.vincularItil(objeto, {
      objetoId: Number(value.replace('#', '')),
      alvo: 'chamado',
      alvoId: chamado.value!.id
    })
  }
  await recarregar()
}

const escreveKb = checkPermi(['itsm:kb:update'])
const podeAtender = checkPermi(['itsm:chamado:atender'])
const kb = reactive({
  visivel: false,
  q: '',
  lista: [] as ItsmApi.KbResumo[],
  usados: [] as ItsmApi.KbResumo[]
})
const buscarKbAgora = async () => {
  kb.lista = (await ItsmApi.buscarKb({ q: kb.q, pageNo: 1, pageSize: 10 })).list
}
let esperaKb: ReturnType<typeof setTimeout> | undefined
const buscarKbDepois = () => {
  clearTimeout(esperaKb)
  esperaKb = setTimeout(buscarKbAgora, 300)
}
const abrirKb = async () => {
  kb.q = chamado.value!.titulo
  kb.visivel = true
  ;[kb.usados] = await Promise.all([ItsmApi.kbDoChamado(chamado.value!.id), buscarKbAgora()])
  if (!kb.lista.length) {
    kb.lista = await ItsmApi.sugestoesKb(chamado.value!.titulo)
  }
}
const usarKb = async (a: ItsmApi.KbResumo, comoSolucao: boolean) => {
  await ItsmApi.usarKbNoChamado({ artigoId: a.id, chamadoId: chamado.value!.id, comoSolucao })
  kb.visivel = false
  message.success(comoSolucao ? 'Solução registrada' : 'Acompanhamento enviado')
  await recarregar()
}
const salvarNaBase = async () => {
  const id = await ItsmApi.criarKbDoChamado(chamado.value!.id)
  message.success('Rascunho criado na base de conhecimento')
  window.open(`/base-conhecimento?artigo=${id}`, '_blank')
}

const pedido = reactive({ visivel: false, aprovadorIds: [] as number[], comentario: '' })
const abrirPedidoAprovacao = () =>
  Object.assign(pedido, { visivel: true, aprovadorIds: [], comentario: '' })
const enviarPedidoAprovacao = async () => {
  await ItsmApi.pedirAprovacao({
    chamadoId: chamado.value!.id,
    aprovadorIds: pedido.aprovadorIds,
    comentario: pedido.comentario || undefined
  })
  pedido.visivel = false
  message.success('Aprovação pedida')
  await recarregar()
}

const removerAtor = async (a: ItsmApi.Ator) => {
  await message.confirm(`Remover ${a.nome} do chamado?`)
  await ItsmApi.removerAtor(a.id)
  await recarregar()
}
</script>

<style scoped>
.kb-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.kb-item :deep(mark) {
  color: inherit;
  background: var(--el-color-warning-light-7);
}
</style>
