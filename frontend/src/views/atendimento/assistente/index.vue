<!-- Atendimento → Configuração → Assistente virtual: tudo do robô num lugar só (substitui os workflows do n8n). -->
<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-8px">
      <div class="flex-1 min-w-260px">
        <div class="text-16px font-bold">Assistente virtual</div>
        <div class="dica"
          >O robô que atende pelo WhatsApp e pelos outros canais: menu do catálogo, consulta e
          atualização dos chamados, avisos e o assistente de IA.</div
        >
      </div>
      <el-button v-hasRole="['super_admin']" v-hasPermi="['atendimento:assistente:config']" @click="exportar"
        ><Icon class="mr-4px" icon="ep:download" />Exportar</el-button
      >
      <el-button v-hasRole="['super_admin']" v-hasPermi="['atendimento:assistente:config']" @click="escolherArquivo"
        ><Icon class="mr-4px" icon="ep:upload" />Importar</el-button
      >
      <input ref="arquivo" accept=".json,application/json" class="hidden" type="file" @change="lerArquivo" />
    </div>
  </ContentWrap>

  <ContentWrap v-loading="carregando">
    <el-tabs v-model="aba">
      <!-- ================= caixas ================= -->
      <el-tab-pane label="Caixas" name="caixas">
        <el-alert
          v-if="tela && !tela.iaAtiva && tela.caixas.some((c) => c.roboAtivo && !c.fluxoAtivo)"
          :closable="false"
          class="mb-10px"
          show-icon
          type="warning"
        >
          Há caixa com o assistente de IA, mas a IA está desligada: essas conversas vão direto para
          as pessoas.
          <el-button link type="primary" @click="router.push('/atendimento/configuracao/ia')"
            >Ligar em Inteligência artificial</el-button
          >
        </el-alert>
        <el-table :data="tela?.caixas || []">
          <el-table-column label="Caixa" min-width="180">
            <template #default="{ row }">
              {{ row.nome }}
              <el-tag v-if="!row.ativo" class="ml-4px" disable-transitions size="small" type="info"
                >desativada</el-tag
              >
              <div class="dica"
                ><Icon :icon="CANAIS[row.canal]?.icone" class="mr-2px" />{{
                  CANAIS[row.canal]?.rotulo
                }}</div
              >
            </template>
          </el-table-column>
          <el-table-column label="Quem atende primeiro" min-width="220">
            <template #default="{ row }">
              <el-tag :type="MODOS[modo(row)].tipo" disable-transitions size="small">{{
                MODOS[modo(row)].rotulo
              }}</el-tag>
              <div v-if="modo(row) === 'ia'" class="dica"
                >{{ row.roboNome || 'Assistente' }} · até {{ row.roboMaxRespostas || 10 }}
                respostas</div
              >
            </template>
          </el-table-column>
          <el-table-column label="Boas-vindas" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.fluxoBoasVindas">{{ row.fluxoBoasVindas }}</span>
              <span v-else class="dica">a padrão (aba Menu e consulta)</span>
            </template>
          </el-table-column>
          <el-table-column label="Avisos do chamado" width="150">
            <template #default="{ row }">
              <el-tag :type="row.avisosAtivo ? 'success' : 'info'" disable-transitions size="small">{{
                row.avisosAtivo ? 'ligados' : 'desligados'
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column align="right" width="90">
            <template #default="{ row }">
              <el-button
                v-hasPermi="['atendimento:assistente:config']"
                link
                type="primary"
                @click="abrirCaixa(row)"
                ><Icon icon="ep:edit" /></el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div class="dica mt-8px"
          >Canal, equipe, horário e pesquisa de satisfação ficam em Configuração → Caixas de
          entrada.</div
        >
      </el-tab-pane>

      <!-- ================= textos ================= -->
      <el-tab-pane v-for="g in GRUPOS" :key="g.nome" :label="g.rotulo" :name="g.nome">
        <div class="dica mb-4px">{{ g.ajuda }}</div>
        <template v-if="g.nome === 'ia'">
          <div class="item-fixo">
            <b>Regras de segurança (fixas)</b>
            <div class="dica mb-6px"
              >Valem acima das orientações gerais e das instruções de cada caixa; nenhuma
              configuração muda.</div
            >
            <pre class="regras">{{ tela?.regrasFixas }}</pre>
          </div>
        </template>
        <TextoAssistente
          v-for="t in doGrupo(g.nome)"
          :key="t.chave"
          v-model="valores[t.chave]"
          :item="t"
        />
        <div v-if="g.nome === 'ia'" class="dica mt-8px"
          >As instruções de cada caixa (nome do assistente, horários, sistemas atendidos) ficam na
          aba Caixas. Provedor e modelo de IA:
          <el-link type="primary" @click="router.push('/atendimento/configuracao/ia')"
            >Inteligência artificial</el-link
          >.</div
        >
        <div class="rodape">
          <el-button
            v-hasPermi="['atendimento:assistente:config']"
            :disabled="!mudados(g.nome).length"
            :loading="salvando"
            type="primary"
            @click="salvar(g.nome)"
            >Salvar {{ mudados(g.nome).length ? `(${mudados(g.nome).length})` : '' }}</el-button
          >
          <el-button :disabled="!mudados(g.nome).length" @click="descartar(g.nome)"
            >Descartar</el-button
          >
        </div>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <!-- ================= robô de uma caixa ================= -->
  <el-drawer v-model="caixa.visivel" :title="`Robô da caixa ${caixa.nome}`" size="560px">
    <el-form label-position="top">
      <el-form-item label="Quem atende primeiro as conversas novas">
        <el-radio-group v-model="caixa.modo">
          <el-radio value="fluxo">Fluxo do catálogo</el-radio>
          <el-radio value="ia">Assistente de IA</el-radio>
          <el-radio value="nenhum">Ninguém (direto para as pessoas)</el-radio>
        </el-radio-group>
        <div class="dica">{{ MODOS[caixa.modo].ajuda }}</div>
      </el-form-item>
      <el-alert
        v-if="caixa.modo === 'ia' && tela && !tela.iaAtiva"
        :closable="false"
        class="mb-12px"
        show-icon
        title="A IA está desligada em Inteligência artificial: até ligar, as conversas vão para as pessoas."
        type="warning"
      />
      <el-form-item v-if="caixa.modo !== 'nenhum'" label="Boas-vindas desta caixa">
        <el-input
          v-model="caixa.dados.fluxoBoasVindas"
          :placeholder="valores['fluxo.boas_vindas'] || 'a padrão'"
          maxlength="1000"
        />
        <div class="dica">Vazio = a padrão, da aba Menu e consulta.</div>
      </el-form-item>
      <template v-if="caixa.modo === 'ia'">
        <el-form-item label="Nome do assistente">
          <el-input v-model="caixa.dados.roboNome" maxlength="60" placeholder="Assistente da TI" />
        </el-form-item>
        <el-form-item label="Instruções desta caixa (prompt)">
          <el-input
            v-model="caixa.dados.roboInstrucoes"
            :autosize="{ minRows: 4, maxRows: 14 }"
            maxlength="4000"
            placeholder="Horários, sistemas atendidos, telefones úteis, como se despedir..."
            show-word-limit
            type="textarea"
          />
          <div class="dica"
            >Somadas às orientações gerais (aba IA). As regras de segurança valem acima das
            duas.</div
          >
        </el-form-item>
        <el-form-item label="Respostas por conversa">
          <el-input-number v-model="caixa.dados.roboMaxRespostas" :max="100" :min="1" />
          <span class="dica ml-8px">depois disso, passa para uma pessoa</span>
        </el-form-item>
      </template>
      <el-form-item label="Avisos do chamado">
        <el-switch v-model="caixa.dados.avisosAtivo" />
        <div class="dica w-full"
          >Andamento, solução e aprovação do chamado aberto pela conversa (textos na aba Avisos do
          chamado). O aprovador recebe o pedido pelo celular do cadastro.</div
        >
      </el-form-item>
      <el-form-item
        v-if="caixa.dados.avisosAtivo && caixa.canal === 'whatsapp_cloud'"
        label="Modelo de aviso (WhatsApp oficial)"
      >
        <el-select
          v-model="caixa.dados.avisoModeloId"
          class="w-full"
          clearable
          placeholder="Sem modelo: depois de 24 h o aviso não sai"
        >
          <el-option
            v-for="m in caixa.modelos"
            :key="m.id"
            :label="`${m.nome} (${m.idioma})`"
            :value="m.id!"
          />
        </el-select>
        <div class="dica"
          >Modelo aprovado com uma variável (o texto do aviso), usado quando já passaram 24 h desde
          a última mensagem do contato.</div
        >
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="caixa.visivel = false">Cancelar</el-button>
      <el-button :loading="salvando" type="primary" @click="salvarCaixa">Salvar</el-button>
    </template>
  </el-drawer>

  <!-- ================= importação ================= -->
  <Dialog v-model="imp.visivel" title="Importar textos do assistente" width="860px">
    <el-alert
      :closable="false"
      class="mb-10px"
      show-icon
      :title="`${imp.linhas.filter((l) => aplica(l)).length} texto(s) mudam; o que não está no arquivo fica como está.`"
      type="info"
    />
    <el-table :data="imp.linhas" max-height="460" size="small">
      <el-table-column label="Texto" min-width="200">
        <template #default="{ row }">
          {{ row.rotulo || row.chave }}
          <div v-if="row.erro" class="dica text-[var(--el-color-danger)]">{{ row.erro }}</div>
        </template>
      </el-table-column>
      <el-table-column label="O que acontece" width="140">
        <template #default="{ row }">
          <el-tag :type="SITUACOES[row.situacao].tipo" disable-transitions size="small">{{
            SITUACOES[row.situacao].rotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Hoje" min-width="200" prop="atual" show-overflow-tooltip />
      <el-table-column label="Arquivo" min-width="200" prop="novo" show-overflow-tooltip />
    </el-table>
    <template #footer>
      <el-button @click="imp.visivel = false">Cancelar</el-button>
      <el-button
        :disabled="!imp.linhas.some((l) => aplica(l))"
        :loading="salvando"
        type="primary"
        @click="importar"
        >Aplicar</el-button
      >
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import { CANAIS } from '@/api/atendimento'
import download from '@/utils/download'
import TextoAssistente from './TextoAssistente.vue'

defineOptions({ name: 'AtendimentoAssistente' })
const message = useMessage()
const router = useRouter()
const route = useRoute()

type Modo = 'fluxo' | 'ia' | 'nenhum'
const MODOS: Record<Modo, { rotulo: string; tipo: 'success' | 'primary' | 'info'; ajuda: string }> =
  {
    fluxo: {
      rotulo: 'Fluxo do catálogo',
      tipo: 'success',
      ajuda:
        'Menu fixo (1 abrir, 2 consultar, 3 dúvida, 4 atendente), perguntas dos formulários do catálogo com validação, consulta e atualização dos chamados. Com a IA ligada, ela só sugere o serviço a partir do texto livre.'
    },
    ia: {
      rotulo: 'Assistente de IA',
      tipo: 'primary',
      ajuda:
        'Conversa livre com o modelo de IA: responde pela base de conhecimento, consulta e abre chamados e passa para uma pessoa quando precisa.'
    },
    nenhum: {
      rotulo: 'Direto para as pessoas',
      tipo: 'info',
      ajuda: 'Sem robô: a conversa nova é distribuída para quem atende a caixa.'
    }
  }
const GRUPOS = [
  {
    nome: 'menu',
    rotulo: 'Menu',
    ajuda: 'Primeiras mensagens do fluxo do catálogo. As opções do menu são fixas; o texto é seu.'
  },
  {
    nome: 'chamado',
    rotulo: 'Consulta e atualização',
    ajuda:
      'Opção 2 do menu ou "#número": o contato vê os chamados dele, a última atualização da equipe, escreve no chamado e aprova ou recusa a solução, com as regras do portal.'
  },
  {
    nome: 'avisos',
    rotulo: 'Avisos do chamado',
    ajuda:
      'Mensagens que saem sozinhas quando o chamado aberto pela conversa anda (no lugar dos workflows 13, 14 e 20 do n8n).'
  },
  {
    nome: 'ia',
    rotulo: 'IA',
    ajuda: 'Prompt do assistente de IA das caixas com o modo "Assistente de IA".'
  }
] as const
const SITUACOES: Record<
  Atd.LinhaImportacaoAssistente['situacao'],
  { rotulo: string; tipo: 'warning' | 'info' | 'success' | 'danger' }
> = {
  muda: { rotulo: 'muda', tipo: 'warning' },
  padrao: { rotulo: 'volta ao padrão', tipo: 'warning' },
  igual: { rotulo: 'igual', tipo: 'info' },
  ignorada: { rotulo: 'ignorado', tipo: 'info' },
  invalida: { rotulo: 'inválido', tipo: 'danger' }
}

const carregando = ref(true)
const salvando = ref(false)
const tela = ref<Atd.TelaAssistente>()
const aba = ref('caixas')
const valores = reactive<Record<string, string>>({})

const carregar = async () => {
  carregando.value = true
  try {
    tela.value = await Atd.getAssistente()
    tela.value.textos.forEach((t) => (valores[t.chave] = t.valor))
  } finally {
    carregando.value = false
  }
}

const doGrupo = (g: string) => (tela.value?.textos || []).filter((t) => t.grupo === g)
const mudados = (g: string) => doGrupo(g).filter((t) => valores[t.chave] !== t.valor)

const salvar = async (g: string) => {
  const enviar: Record<string, string> = {}
  mudados(g).forEach((t) => (enviar[t.chave] = valores[t.chave]))
  salvando.value = true
  try {
    await Atd.salvarTextosAssistente(enviar)
    message.success('Salvo. O robô passa a usar em até um minuto.')
    await carregar()
  } finally {
    salvando.value = false
  }
}

const descartar = (g: string) => doGrupo(g).forEach((t) => (valores[t.chave] = t.valor))

// ---------- caixa ----------
const modo = (c: Atd.Caixa): Modo => (c.fluxoAtivo ? 'fluxo' : c.roboAtivo ? 'ia' : 'nenhum')
const caixa = reactive({
  visivel: false,
  nome: '',
  canal: '',
  modo: 'nenhum' as Modo,
  modelos: [] as Atd.Modelo[],
  dados: {} as Atd.CaixaRobo
})

const abrirCaixa = async (c: Atd.Caixa) => {
  caixa.nome = c.nome
  caixa.canal = c.canal
  caixa.modo = modo(c)
  caixa.dados = {
    id: c.id!,
    fluxoBoasVindas: c.fluxoBoasVindas,
    roboNome: c.roboNome,
    roboInstrucoes: c.roboInstrucoes,
    roboMaxRespostas: c.roboMaxRespostas || 10,
    avisosAtivo: c.avisosAtivo !== false,
    avisoModeloId: c.avisoModeloId
  }
  caixa.modelos = []
  caixa.visivel = true
  if (c.canal === 'whatsapp_cloud') {
    caixa.modelos = (await Atd.getModelosDaCaixa(c.id!)).filter(
      (m) => m.status === 'APPROVED' && m.variaveis === 1
    )
  }
}

const salvarCaixa = async () => {
  salvando.value = true
  try {
    await Atd.salvarCaixaRobo({
      ...caixa.dados,
      fluxoAtivo: caixa.modo === 'fluxo',
      roboAtivo: caixa.modo === 'ia'
    })
    message.success('Caixa salva')
    caixa.visivel = false
    await carregar()
  } finally {
    salvando.value = false
  }
}

// ---------- exportar / importar ----------
const arquivo = ref<HTMLInputElement>()
const imp = reactive({
  visivel: false,
  arq: undefined as Atd.ArquivoAssistente | undefined,
  linhas: [] as Atd.LinhaImportacaoAssistente[]
})
const aplica = (l: Atd.LinhaImportacaoAssistente) => l.situacao === 'muda' || l.situacao === 'padrao'

const exportar = async () => {
  const a = await Atd.exportarAssistente()
  const dia = new Date().toISOString().slice(0, 10)
  download.json(new Blob([JSON.stringify(a, null, 2)]), `assistente-virtual-${dia}.json`)
}

const escolherArquivo = () => {
  if (arquivo.value) {
    arquivo.value.value = ''
    arquivo.value.click()
  }
}

const lerArquivo = async (ev: Event) => {
  const f = (ev.target as HTMLInputElement).files?.[0]
  if (!f) return
  let arq: Atd.ArquivoAssistente
  try {
    arq = JSON.parse(await f.text())
  } catch {
    message.error('O arquivo não é um JSON válido.')
    return
  }
  imp.arq = arq
  imp.linhas = await Atd.previaImportacaoAssistente(arq)
  imp.visivel = true
}

const importar = async () => {
  salvando.value = true
  try {
    const r = await Atd.importarAssistente(imp.arq!)
    message.success(`${r.alterados} texto(s) importado(s)`)
    imp.visivel = false
    await carregar()
  } finally {
    salvando.value = false
  }
}

onMounted(async () => {
  await carregar()
  const id = Number(route.query.caixa)
  const c = id ? tela.value?.caixas.find((x) => x.id === id) : undefined
  if (c) abrirCaixa(c)
})
</script>

<style lang="scss" scoped>
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.rodape {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  padding: 10px 0;
  background: var(--el-bg-color);
  gap: 8px;
}
.item-fixo {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.regras {
  padding: 10px;
  margin: 0;
  font-family: inherit;
  font-size: 12px;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
