<template>
  <el-drawer v-model="visivel" :size="largura" destroy-on-close @closed="item = undefined">
    <template #header>
      <div v-if="item" class="min-w-0">
        <div class="truncate text-17px font-bold">
          {{ ehMudanca ? 'Mudança' : 'Problema' }} #{{ item.id }} · {{ item.titulo }}
        </div>
        <div class="mt-6px flex flex-wrap items-center gap-6px">
          <el-tag :type="corStatusItil(objeto, item.status)" disable-transitions>{{
            item.statusRotulo
          }}</el-tag>
          <el-tag :type="corPrioridade(item.prioridade)" disable-transitions effect="plain">
            Prioridade {{ item.prioridadeRotulo.toLowerCase() }}
          </el-tag>
          <el-tag
            v-if="ehMudanca"
            :type="item.tipo === 3 ? 'danger' : 'info'"
            disable-transitions
            effect="plain"
            >{{ item.tipoRotulo }}</el-tag
          >
          <el-tag
            v-if="ehMudanca && item.validacao && item.validacao > 1"
            :type="corAprovacao(item.validacao)"
            disable-transitions
            >{{ item.validacaoRotulo }}</el-tag
          >
          <el-tag v-if="item.atrasado" disable-transitions effect="dark" type="danger"
            >Prazo vencido</el-tag
          >
        </div>
      </div>
    </template>

    <div v-if="item" v-loading="carregando" class="flex gap-16px">
      <!-- coluna principal -->
      <div class="min-w-0 flex-1">
        <div class="mb-12px flex flex-wrap items-center gap-8px">
          <el-button
            v-if="pode('assumir')"
            type="primary"
            @click="acao(() => ItsmApi.assumirItil(objeto, item!.id))"
          >
            <Icon class="mr-4px" icon="ep:pointer" />Assumir
          </el-button>
          <el-select
            v-if="pode('status')"
            :model-value="item.status"
            class="!w-200px"
            @change="mudarStatus"
          >
            <el-option
              v-for="s in statusItil(objeto)"
              :key="s.valor"
              :disabled="!s.manual"
              :label="s.rotulo"
              :value="s.valor"
            />
          </el-select>
          <el-button v-if="pode('pedir-aprovacao')" @click="abrirPedido">
            <Icon class="mr-4px" icon="ep:select" />Pedir aprovação
          </el-button>
          <el-button
            v-if="pode('fechar')"
            @click="acao(() => ItsmApi.fecharItil(objeto, item!.id), 'Fechar?')"
          >
            <Icon class="mr-4px" icon="ep:circle-check" />Fechar
          </el-button>
          <el-button v-if="pode('cancelar')" @click="cancelar">
            <Icon class="mr-4px" icon="ep:circle-close" />Cancelar mudança
          </el-button>
          <el-button
            v-if="pode('reabrir')"
            @click="acao(() => ItsmApi.reabrirItil(objeto, item!.id), 'Reabrir?')"
          >
            <Icon class="mr-4px" icon="ep:refresh-left" />Reabrir
          </el-button>
          <el-button v-if="pode('excluir')" plain type="danger" @click="excluir">
            <Icon class="mr-4px" icon="ep:delete" />Excluir
          </el-button>
        </div>

        <el-alert
          v-if="item.conflitos?.length"
          :closable="false"
          class="mb-12px"
          show-icon
          type="warning"
        >
          <template #title>Outras mudanças na mesma janela</template>
          <div v-for="c in item.conflitos" :key="c.objetoId">
            <el-link :href="`/servicedesk/mudancas?id=${c.objetoId}`" target="_blank" type="primary"
              >#{{ c.objetoId }} {{ c.titulo }}</el-link
            >
            · {{ formatDate(c.inicioPrevisto!, 'DD/MM HH:mm') }} –
            {{ formatDate(c.fimPrevisto!, 'DD/MM HH:mm') }} ({{ c.statusRotulo }})
          </div>
        </el-alert>

        <el-tabs v-model="aba">
          <el-tab-pane label="Linha do tempo" name="tempo">
            <div class="mb-12px" v-html="item.descricao || '<i>Sem descrição</i>'"></div>
            <el-divider class="!my-12px" />
            <LinhaDoTempo :itens="linha" />

            <el-card v-if="tiposPermitidos.length" class="mt-12px" shadow="never">
              <el-radio-group v-model="nova.tipo" class="mb-10px" size="small">
                <el-radio-button v-for="t in tiposPermitidos" :key="t.valor" :value="t.valor">{{
                  t.rotulo
                }}</el-radio-button>
              </el-radio-group>
              <Editor :key="editorKey" v-model="nova.conteudo" :directory="objeto" height="170px" />
              <div class="mt-10px flex flex-wrap items-center gap-12px">
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
                <el-checkbox
                  v-if="nova.tipo === 'solucao' && item.totalChamados > 0"
                  v-model="nova.solucionarChamados"
                >
                  Solucionar também os {{ item.totalChamados }} chamado(s) vinculado(s) em aberto
                </el-checkbox>
                <el-button :loading="enviando" class="ml-auto" type="primary" @click="enviar">
                  {{ rotuloEnviar }}
                </el-button>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane :label="ehMudanca ? 'Planejamento' : 'Análise'" name="analise">
            <div v-for="c in camposTexto" :key="c.campo" class="mb-16px">
              <div class="mb-4px text-13px font-bold">{{ c.rotulo }}</div>
              <div
                v-if="(item as any)[c.campo]"
                class="texto"
                v-html="(item as any)[c.campo]"
              ></div>
              <div v-else class="text-13px text-[var(--el-text-color-placeholder)]">
                {{ c.vazio }}
              </div>
            </div>
            <el-button v-if="pode('editar')" @click="aba = 'dados'">Editar</el-button>
          </el-tab-pane>

          <el-tab-pane v-if="pode('editar')" label="Dados" name="dados">
            <el-form :model="edicao" label-width="130px">
              <el-form-item label="Título"
                ><el-input v-model="edicao.titulo" maxlength="255"
              /></el-form-item>
              <el-form-item v-if="ehMudanca" label="Tipo">
                <el-radio-group v-model="edicao.tipo">
                  <el-radio-button v-for="t in TIPOS_MUDANCA" :key="t.valor" :value="t.valor">{{
                    t.rotulo
                  }}</el-radio-button>
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
              <el-form-item v-if="ehMudanca" label="Janela">
                <el-date-picker
                  v-model="edicao.janela"
                  end-placeholder="Fim"
                  start-placeholder="Início previsto"
                  type="datetimerange"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </el-form-item>
              <el-form-item label="Prazo">
                <el-date-picker
                  v-model="edicao.prazo"
                  placeholder="Sem prazo"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </el-form-item>
              <el-form-item label="Descrição">
                <Editor v-model="edicao.descricao" :directory="objeto" height="180px" />
              </el-form-item>
              <el-form-item v-for="c in camposTexto" :key="c.campo" :label="c.rotulo">
                <Editor v-model="edicao[c.campo]" :directory="objeto" height="150px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="salvarEdicao">Salvar</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane :label="`Vínculos (${vinculos.length})`" name="vinculos">
            <div v-if="pode('vincular')" class="mb-10px flex flex-wrap items-center gap-8px">
              <el-select v-model="novoVinculo.alvo" class="!w-140px">
                <el-option label="Chamado" value="chamado" />
                <el-option v-if="ehMudanca" label="Problema" value="problema" />
              </el-select>
              <el-input-number
                v-model="novoVinculo.alvoId"
                :controls="false"
                :min="1"
                class="!w-120px"
                placeholder="Nº"
              />
              <el-button :disabled="!novoVinculo.alvoId" @click="vincular">Vincular</el-button>
            </div>
            <el-table :data="vinculos" size="small">
              <el-table-column label="Item" width="150">
                <template #default="{ row }"
                  >{{ ROTULO_OBJETO[row.objeto] }} #{{ row.objetoId }}</template
                >
              </el-table-column>
              <el-table-column label="Título" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-link v-if="row.titulo" :href="linkDe(row)" target="_blank" type="primary">{{
                    row.titulo
                  }}</el-link>
                  <span v-else class="text-[var(--el-text-color-placeholder)]"
                    >fora da sua unidade</span
                  >
                </template>
              </el-table-column>
              <el-table-column label="Situação" prop="statusRotulo" width="170" />
              <el-table-column v-if="pode('vincular')" width="60">
                <template #default="{ row }">
                  <el-button link type="danger" @click="desvincular(row)"
                    ><Icon icon="ep:close"
                  /></el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane
            v-if="ehMudanca"
            :label="`Aprovações (${aprovacoes.length})`"
            name="aprovacoes"
          >
            <el-empty
              v-if="!aprovacoes.length"
              :image-size="60"
              description="Nenhuma aprovação pedida"
            />
            <el-table v-else :data="aprovacoes" size="small">
              <el-table-column label="Aprovador" min-width="150" prop="aprovador" />
              <el-table-column label="Situação" width="120">
                <template #default="{ row }">
                  <el-tag :type="corAprovacao(row.status)" disable-transitions size="small">{{
                    ROTULO_APROVACAO[row.status]
                  }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Pedido" min-width="180" show-overflow-tooltip>
                <template #default="{ row }"
                  >{{ formatDate(row.pedidoEm) }} por {{ row.solicitadoPorNome
                  }}<span v-if="row.comentarioPedido">: {{ row.comentarioPedido }}</span></template
                >
              </el-table-column>
              <el-table-column label="Resposta" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="row.respondidoEm"
                    >{{ formatDate(row.respondidoEm)
                    }}<span v-if="row.comentarioResposta"
                      >: {{ row.comentarioResposta }}</span
                    ></template
                  >
                  <span v-else class="text-[var(--el-text-color-placeholder)]">aguardando</span>
                </template>
              </el-table-column>
            </el-table>
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
              <el-table-column label="O quê" width="150">
                <template #default="{ row }">{{ CAMPOS[row.campo] || row.campo }}</template>
              </el-table-column>
              <el-table-column label="De" prop="valorAntigo" show-overflow-tooltip />
              <el-table-column label="Para" prop="valorNovo" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- coluna lateral -->
      <div class="w-290px shrink-0">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Categoria">{{ item.categoria || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Unidade">{{ item.dept || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Registrado">
            {{ formatDate(item.abertoEm) }}
            <div class="text-12px text-[var(--el-text-color-secondary)]">{{
              item.registradoPorNome
            }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="ehMudanca" label="Janela">
            <template v-if="item.inicioPrevisto">
              {{ formatDate(item.inicioPrevisto, 'DD/MM/YYYY HH:mm') }}<br />até
              {{ formatDate(item.fimPrevisto!, 'DD/MM/YYYY HH:mm') }}
            </template>
            <template v-else>não marcada</template>
          </el-descriptions-item>
          <el-descriptions-item label="Prazo">
            <span
              v-if="item.prazo"
              :class="{ 'text-[var(--el-color-danger)] font-bold': item.atrasado }"
            >
              {{ formatDate(item.prazo) }}
            </span>
            <template v-else>—</template>
          </el-descriptions-item>
          <el-descriptions-item
            v-if="item.solucionadoEm"
            :label="ehMudanca ? 'Aplicada' : 'Solucionado'"
          >
            {{ formatDate(item.solucionadoEm) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="item.fechadoEm" label="Encerrado">{{
            formatDate(item.fechadoEm)
          }}</el-descriptions-item>
          <el-descriptions-item label="Tempo gasto">{{
            duracao(item.tempoGastoSeg)
          }}</el-descriptions-item>
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
              v-for="a in (item as any)[g.campo]"
              :key="a.id"
              :closable="pode('atribuir')"
              disable-transitions
              effect="plain"
              @close="removerAtor(a)"
            >
              <Icon :icon="a.equipeId ? 'ep:user-filled' : 'ep:user'" class="mr-2px" />{{ a.nome }}
            </el-tag>
            <span
              v-if="!(item as any)[g.campo].length"
              class="text-12px text-[var(--el-text-color-placeholder)]"
              >ninguém</span
            >
          </div>
        </div>
      </div>
    </div>

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

    <!-- pedir aprovação -->
    <Dialog v-model="pedido.visivel" title="Pedir aprovação da mudança" width="460px">
      <el-form label-position="top">
        <el-form-item label="Quem aprova (todos precisam aprovar; uma recusa recusa)">
          <UsuarioSelect
            v-model="pedido.aprovadorIds"
            class="w-full"
            filterable
            multiple
            placeholder="Pessoas"
          />
        </el-form-item>
        <el-form-item label="O que precisa ser avaliado">
          <el-input v-model="pedido.comentario" maxlength="2000" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="!pedido.aprovadorIds.length" type="primary" @click="enviarPedido"
          >Pedir</el-button
        >
        <el-button @click="pedido.visivel = false">Cancelar</el-button>
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
  ESCALA,
  PRIORIDADES,
  TIPOS_MUDANCA,
  corAprovacao,
  corPrioridade,
  corStatusItil,
  duracao,
  statusItil,
  type ObjetoItil
} from '@/api/itsm'
import LinhaDoTempo from '../components/LinhaDoTempo.vue'

defineOptions({ name: 'ItilDetalhe' })
const props = defineProps<{ objeto: ObjetoItil }>()
const emit = defineEmits(['alterado'])
const message = useMessage()
const ehMudanca = computed(() => props.objeto === 'mudanca')

const GRUPOS = [
  { tipo: 2, rotulo: 'Técnicos', campo: 'tecnicos' },
  { tipo: 1, rotulo: 'Solicitantes', campo: 'solicitantes' },
  { tipo: 3, rotulo: 'Observadores', campo: 'observadores' }
]
const ROTULO_OBJETO: Record<string, string> = {
  chamado: 'Chamado',
  problema: 'Problema',
  mudanca: 'Mudança'
}
const ROTULO_APROVACAO: Record<number, string> = { 2: 'Aguardando', 3: 'Aprovada', 4: 'Recusada' }
const CAMPOS: Record<string, string> = {
  criado: 'Registro',
  status: 'Situação',
  validacao: 'Aprovação (geral)',
  titulo: 'Título',
  tipo: 'Tipo',
  urgencia: 'Urgência',
  impacto: 'Impacto',
  prioridade: 'Prioridade',
  categoria: 'Categoria',
  unidade: 'Unidade',
  prazo: 'Prazo',
  inicio: 'Início previsto',
  fim: 'Fim previsto',
  descricao: 'Descrição',
  impactos: 'Impactos',
  causas: 'Causas',
  sintomas: 'Sintomas',
  analise: 'Análise de impacto',
  controle: 'Plano de controle',
  implantacao: 'Plano de implantação',
  retorno: 'Plano de retorno',
  checklist: 'Checklist',
  'tecnico+': '+ Técnico',
  'tecnico-': '− Técnico',
  'solicitante+': '+ Solicitante',
  'solicitante-': '− Solicitante',
  'observador+': '+ Observador',
  'observador-': '− Observador',
  'chamado+': '+ Chamado',
  'chamado-': '− Chamado',
  'problema+': '+ Problema',
  'problema-': '− Problema',
  'mudanca+': '+ Mudança',
  'mudanca-': '− Mudança',
  'aprovacao+': '+ Aprovador',
  'aprovacao-': 'Pedidos retirados',
  aprovacao: 'Aprovação',
  'chamados-solucionados': 'Chamados solucionados'
}

const camposTexto = computed(() =>
  ehMudanca.value
    ? [
        {
          campo: 'analiseImpacto',
          rotulo: 'Análise de impacto',
          vazio: 'O que pode ser afetado e por quanto tempo'
        },
        {
          campo: 'planoImplantacao',
          rotulo: 'Plano de implantação',
          vazio: 'Passo a passo da execução'
        },
        { campo: 'planoRetorno', rotulo: 'Plano de retorno', vazio: 'Como desfazer se der errado' },
        {
          campo: 'planoControle',
          rotulo: 'Plano de controle',
          vazio: 'Como confirmar que deu certo'
        },
        { campo: 'checklist', rotulo: 'Checklist', vazio: 'Itens a conferir antes e depois' }
      ]
    : [
        {
          campo: 'sintomas',
          rotulo: 'Sintomas',
          vazio: 'Como o problema aparece para os usuários'
        },
        { campo: 'causas', rotulo: 'Causas', vazio: 'Causa raiz (quando descoberta)' },
        { campo: 'impactos', rotulo: 'Impactos', vazio: 'Serviços e unidades afetados' }
      ]
)

const visivel = ref(false)
const carregando = ref(false)
const enviando = ref(false)
const aba = ref('tempo')
const item = ref<ItsmApi.ItemItil>()
const linha = ref<ItsmApi.Interacao[]>([])
const vinculos = ref<ItsmApi.VinculoItil[]>([])
const aprovacoes = ref<ItsmApi.AprovacaoMudanca[]>([])
const historico = ref<ItsmApi.Historico[]>([])
const categorias = ref<ItsmApi.Categoria[]>([])
const equipes = ref<ItsmApi.Equipe[]>([])
const edicao = ref<any>({})
const editorKey = ref(0)
const largura = computed(() => (window.innerWidth < 1200 ? '100%' : '75%'))
const novoVinculo = reactive({
  alvo: 'chamado' as 'chamado' | 'problema',
  alvoId: undefined as number | undefined
})

const novaVazia = () => ({
  tipo: 'acompanhamento',
  conteudo: '',
  tempoGastoMin: 0,
  estadoTarefa: 2,
  periodo: [] as string[],
  solucionarChamados: false
})
const nova = ref(novaVazia())
const atorDialogo = reactive({
  visivel: false,
  tipo: 2,
  rotulo: '',
  modo: 'pessoa',
  userId: undefined as number | undefined,
  equipeId: undefined as number | undefined
})
const pedido = reactive({ visivel: false, aprovadorIds: [] as number[], comentario: '' })

const pode = (a: string) => !!item.value?.acoes.includes(a)
const tiposPermitidos = computed(() =>
  [
    { valor: 'acompanhamento', rotulo: 'Acompanhamento', acao: 'acompanhar' },
    { valor: 'tarefa', rotulo: 'Tarefa', acao: 'tarefa' },
    { valor: 'solucao', rotulo: ehMudanca.value ? 'Aplicação' : 'Solução', acao: 'solucionar' }
  ].filter((t) => pode(t.acao))
)
const rotuloEnviar = computed(() =>
  nova.value.tipo === 'solucao'
    ? ehMudanca.value
      ? 'Registrar aplicação'
      : 'Registrar solução'
    : 'Enviar'
)
const linkDe = (v: ItsmApi.VinculoItil) =>
  v.objeto === 'chamado'
    ? `/servicedesk/chamados?id=${v.objetoId}`
    : `/servicedesk/${v.objeto === 'problema' ? 'problemas' : 'mudancas'}?id=${v.objetoId}`

const carregar = async (id: number) => {
  carregando.value = true
  try {
    const [i, l, v, a] = await Promise.all([
      ItsmApi.getItil(props.objeto, id),
      ItsmApi.getItilLinha(props.objeto, id),
      ItsmApi.getItilVinculos(props.objeto, id),
      ehMudanca.value ? ItsmApi.getAprovacoesMudanca(id) : Promise.resolve([])
    ])
    item.value = i
    linha.value = l
    vinculos.value = v
    aprovacoes.value = a
    edicao.value = {
      id: i.id,
      titulo: i.titulo,
      tipo: i.tipo,
      categoriaId: i.categoriaId,
      urgencia: i.urgencia,
      impacto: i.impacto,
      prioridade: i.prioridade,
      prazo: i.prazo ? formatDate(i.prazo, 'YYYY-MM-DD HH:mm:ss') : undefined,
      janela: i.inicioPrevisto
        ? [
            formatDate(i.inicioPrevisto, 'YYYY-MM-DD HH:mm:ss'),
            formatDate(i.fimPrevisto!, 'YYYY-MM-DD HH:mm:ss')
          ]
        : [],
      descricao: i.descricao,
      impactos: i.impactos,
      causas: i.causas,
      sintomas: i.sintomas,
      analiseImpacto: i.analiseImpacto,
      planoControle: i.planoControle,
      planoImplantacao: i.planoImplantacao,
      planoRetorno: i.planoRetorno,
      checklist: i.checklist
    }
    if (aba.value === 'historico')
      historico.value = await ItsmApi.getItilHistorico(props.objeto, id)
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
  if (a === 'historico' && item.value)
    historico.value = await ItsmApi.getItilHistorico(props.objeto, item.value.id)
})

const recarregar = async () => {
  await carregar(item.value!.id)
  emit('alterado')
}

const acao = async (fn: () => Promise<any>, confirmar?: string) => {
  if (confirmar) await message.confirm(confirmar)
  await fn()
  await recarregar()
}

const mudarStatus = (s: number) =>
  acao(() => ItsmApi.statusItilMudar(props.objeto, item.value!.id, s))

const cancelar = async () => {
  const { value } = await ElMessageBox.prompt('Motivo do cancelamento', 'Cancelar mudança', {
    confirmButtonText: 'Cancelar a mudança',
    cancelButtonText: 'Voltar'
  })
  await ItsmApi.cancelarMudanca(item.value!.id, value)
  await recarregar()
}

const excluir = async () => {
  await message.delConfirm(
    `Excluir ${ehMudanca.value ? 'a mudança' : 'o problema'} #${item.value!.id}?`
  )
  await ItsmApi.excluirItil(props.objeto, item.value!.id)
  visivel.value = false
  emit('alterado')
}

const enviar = async () => {
  if (!nova.value.conteudo || nova.value.conteudo === '<p><br></p>') {
    message.warning('Escreva o texto')
    return
  }
  enviando.value = true
  try {
    const n = nova.value
    await ItsmApi.interacaoItil(props.objeto, {
      objetoId: item.value!.id,
      tipo: n.tipo,
      conteudo: n.conteudo,
      tempoGastoSeg: n.tipo === 'tarefa' ? n.tempoGastoMin * 60 : undefined,
      estadoTarefa: n.tipo === 'tarefa' ? (n.periodo?.length ? 1 : n.estadoTarefa) : undefined,
      inicio: n.tipo === 'tarefa' && n.periodo?.length ? n.periodo[0] : undefined,
      fim: n.tipo === 'tarefa' && n.periodo?.length ? n.periodo[1] : undefined,
      solucionarChamados: n.tipo === 'solucao' && n.solucionarChamados
    })
    nova.value = novaVazia()
    editorKey.value++
    await recarregar()
  } finally {
    enviando.value = false
  }
}

const salvarEdicao = async () => {
  const e = edicao.value
  await ItsmApi.atualizarItil(props.objeto, {
    ...e,
    inicioPrevisto: e.janela?.length ? e.janela[0] : undefined,
    fimPrevisto: e.janela?.length ? e.janela[1] : undefined,
    janela: undefined
  })
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
  await ItsmApi.adicionarAtorItil(props.objeto, {
    objetoId: item.value!.id,
    tipo: atorDialogo.tipo,
    userId: atorDialogo.modo === 'pessoa' ? atorDialogo.userId : undefined,
    equipeId: atorDialogo.modo === 'equipe' ? atorDialogo.equipeId : undefined
  })
  atorDialogo.visivel = false
  await recarregar()
}

const removerAtor = async (a: ItsmApi.Ator) => {
  await message.confirm(`Remover ${a.nome}?`)
  await ItsmApi.removerAtorItil(props.objeto, a.id)
  await recarregar()
}

const vincular = async () => {
  await ItsmApi.vincularItil(props.objeto, {
    objetoId: item.value!.id,
    alvo: novoVinculo.alvo,
    alvoId: novoVinculo.alvoId!
  })
  novoVinculo.alvoId = undefined
  await recarregar()
}

const desvincular = async (v: ItsmApi.VinculoItil) => {
  await message.confirm(
    `Desfazer o vínculo com ${ROTULO_OBJETO[v.objeto].toLowerCase()} #${v.objetoId}?`
  )
  await ItsmApi.desvincularItil(props.objeto, v.id)
  await recarregar()
}

const abrirPedido = () => Object.assign(pedido, { visivel: true, aprovadorIds: [], comentario: '' })
const enviarPedido = async () => {
  await ItsmApi.pedirAprovacaoMudanca({
    mudancaId: item.value!.id,
    aprovadorIds: pedido.aprovadorIds,
    comentario: pedido.comentario || undefined
  })
  pedido.visivel = false
  message.success('Aprovação pedida')
  await recarregar()
}
</script>

<style scoped>
.texto {
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
.texto :deep(p) {
  margin: 0 0 6px;
}
</style>
