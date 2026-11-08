<template>
  <ContentWrap v-loading="loading">
    <template v-if="cfg">
      <div v-for="u in USOS" :key="u.titulo" class="uso">
        <div class="uso-topo">
          <b>{{ u.titulo }}</b>
          <el-switch v-model="geral[u.ligado]" :active-text="geral[u.ligado] ? 'Ligado' : 'Desligado'" @change="salvarGeral" />
          <span class="dica">{{ u.ajuda }}</span>
        </div>
        <el-form inline>
          <template v-for="c in u.campos" :key="c.principal">
            <el-form-item :label="`${c.rotulo}: principal`">
              <el-select v-model="geral[c.principal]" class="!w-220px" clearable placeholder="qualquer ativo, pela prioridade" @change="salvarGeral">
                <el-option v-for="p in cfg.provedores" :key="p.id" :label="p.nome" :value="p.id!" />
              </el-select>
            </el-form-item>
            <el-form-item label="reserva">
              <el-select v-model="geral[c.reserva]" :disabled="!geral[c.principal]" class="!w-200px" clearable placeholder="nenhuma" @change="salvarGeral">
                <el-option v-for="p in cfg.provedores.filter((x) => x.id !== geral[c.principal])" :key="p.id" :label="p.nome" :value="p.id!" />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </div>
      <div class="dica"
        >Com o principal escolhido, só ele e a reserva são usados: o Assistente e o Atendimento nunca usam a credencial um do
        outro. Sem principal, os provedores ativos são tentados pela prioridade (menor primeiro).</div
      >
    </template>
  </ContentWrap>

  <ContentWrap title="Provedores">
    <el-button class="mb-10px" plain type="primary" @click="abrir()"
      ><Icon class="mr-5px" icon="ep:plus" /> Novo provedor</el-button
    >
    <el-table :data="cfg?.provedores || []">
      <el-table-column label="Prioridade" prop="prioridade" width="95" />
      <el-table-column label="Provedor" min-width="200">
        <template #default="{ row }">
          <div class="font-bold">{{ row.nome }}</div>
          <div class="dica">{{ rotuloTipo(row.tipo) }} · {{ row.modelo }}</div>
        </template>
      </el-table-column>
      <el-table-column label="Endereço" min-width="220" prop="baseUrl" show-overflow-tooltip />
      <el-table-column label="Situação" width="200">
        <template #default="{ row }">
          <el-tag v-if="!row.ativo" disable-transitions size="small" type="info">desligado</el-tag>
          <template v-else-if="row.testadoEm">
            <el-tag v-if="!row.erroTeste" disable-transitions size="small" type="success"
              >funcionando</el-tag
            >
            <el-tooltip v-else :content="row.erroTeste"
              ><el-tag disable-transitions size="small" type="danger">com erro</el-tag></el-tooltip
            >
          </template>
          <span v-else class="dica">não testado</span>
          <el-tag v-if="row.id === cfg?.provedorInternoId" class="ml-4px" disable-transitions size="small" type="success"
            >Assistente IA</el-tag
          >
          <el-tag v-if="row.id === cfg?.provedorAssistenteId" class="ml-4px" disable-transitions size="small"
            >robô do atendimento</el-tag
          >
          <el-tag v-if="row.id === cfg?.provedorCopilotoId" class="ml-4px" disable-transitions size="small"
            >copiloto</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column width="150">
        <template #default="{ row }">
          <el-button :loading="testando === row.id" link type="primary" @click="testar(row)"
            >Testar</el-button
          >
          <el-button link type="primary" @click="abrir(row)"><Icon icon="ep:edit" /></el-button>
          <el-button link type="danger" @click="excluir(row)"><Icon icon="ep:delete" /></el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap title="Uso nos últimos 30 dias">
    <el-empty v-if="!cfg?.uso.length" :image-size="60" description="Nenhuma chamada ainda" />
    <el-table v-else :data="cfg.uso" size="small">
      <el-table-column label="Provedor" min-width="180" prop="provedor" />
      <el-table-column label="Uso" width="140">
        <template #default="{ row }">{{ FINALIDADES[row.finalidade] || row.finalidade }}</template>
      </el-table-column>
      <el-table-column align="right" label="Chamadas" prop="chamadas" width="100" />
      <el-table-column align="right" label="Com erro" prop="erros" width="100" />
      <el-table-column align="right" label="Tokens enviados" prop="tokensEntrada" width="140" />
      <el-table-column align="right" label="Tokens recebidos" prop="tokensSaida" width="140" />
    </el-table>
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar provedor' : 'Novo provedor'"
    width="640px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="130px">
      <el-form-item label="Tipo">
        <el-select v-model="form.dados.tipo" class="w-full" @change="trocarTipo">
          <el-option v-for="t in cfg?.tipos" :key="t.codigo" :label="t.rotulo" :value="t.codigo" />
        </el-select>
      </el-form-item>
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="80"
      /></el-form-item>
      <el-form-item label="Endereço">
        <el-input v-model="form.dados.baseUrl" :placeholder="tipoAtual?.baseUrl || 'https://...'" />
        <div class="dica">Em branco: o endereço padrão do tipo.</div>
        <div v-if="form.dados.tipo === 'ollama'" class="dica"
          >Ollama no próprio servidor: <code>http://host.docker.internal:11434/v1</code>. Em outra máquina, use o IP dela, e o
          Ollama precisa escutar na rede (<code>OLLAMA_HOST=0.0.0.0</code>). Ollama instalado como serviço no Coolify: ligue
          "Connect To Predefined Network" nele e no CO-TI e use o nome do contêiner, ex.:
          <code>http://ollama-api-xxxx:11434/v1</code>.</div
        >
      </el-form-item>
      <el-form-item label="Chave de API">
        <el-input
          v-model="form.dados.chave"
          :placeholder="
            form.dados.chaveDefinida
              ? 'definida — deixe em branco para manter'
              : tipoAtual?.exigeChave
                ? ''
                : 'opcional'
          "
          show-password
          type="password"
        />
        <div v-if="form.dados.tipo === 'ollama'" class="dica"
          >O Ollama não pede chave. Informe só se ele estiver atrás de um proxy que exige uma (vai como
          <code>Authorization: Bearer</code>).</div
        >
      </el-form-item>
      <el-form-item label="Modelo" prop="modelo">
        <el-input
          v-model="form.dados.modelo"
          :placeholder="tipoAtual?.exemploModelo"
          maxlength="150"
        />
      </el-form-item>
      <el-form-item label="Modelo de áudio">
        <el-input
          v-model="form.dados.modeloAudio"
          maxlength="150"
          placeholder="ex.: whisper-1 (opcional)"
        />
        <div class="dica"
          >Transcreve os áudios recebidos (/audio/transcriptions). OpenAI, Groq e servidores
          compatíveis oferecem; em branco, este provedor não transcreve.</div
        >
      </el-form-item>
      <el-form-item label="Temperatura">
        <el-input-number
          v-model="form.dados.temperatura"
          :max="2"
          :min="0"
          :precision="2"
          :step="0.1"
        />
        <span class="dica ml-8px">menor = respostas mais previsíveis</span>
      </el-form-item>
      <el-form-item label="Tokens da resposta">
        <el-input-number v-model="form.dados.maxTokens" :max="8000" :min="50" :step="100" />
      </el-form-item>
      <el-form-item label="Desligar raciocínio">
        <el-switch v-model="form.dados.semRaciocinio" />
        <div class="dica"
          >Para modelos que "pensam" antes de responder (Qwen3.5, DeepSeek-R1, GPT-OSS): responde direto, mais rápido
          e sem gastar os tokens da resposta no raciocínio. Use com Ollama ou OpenRouter; se o Testar der erro,
          o modelo ou o provedor não aceita a opção e ela deve ficar desligada.</div
        >
      </el-form-item>
      <el-form-item label="Prioridade">
        <el-input-number v-model="form.dados.prioridade" :max="999" :min="0" />
        <span class="dica ml-8px">ordem da reserva (menor primeiro)</span>
      </el-form-item>
      <el-form-item label="Ativo"><el-switch v-model="form.dados.ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'

defineOptions({ name: 'AtendimentoIa' })
const message = useMessage()
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  modelo: [{ required: true, message: 'Informe o modelo', trigger: 'blur' }]
}
const FINALIDADES: Record<string, string> = {
  interno: 'Assistente IA',
  assistente: 'Robô do atendimento',
  transcricao: 'Transcrição',
  sugerir: 'Sugerir resposta',
  resumir: 'Resumir',
  reescrever: 'Reescrever',
  teste: 'Teste'
}

const loading = ref(true)
const cfg = ref<Atd.IaConfig>()
type Geral = Record<string, any>
const geral = reactive<Geral>({ ativo: false, internoAtivo: false })
const CAMPOS_GERAL = ['ativo', 'internoAtivo', 'provedorInternoId', 'provedorInternoReservaId', 'provedorAssistenteId',
  'provedorAssistenteReservaId', 'provedorCopilotoId', 'provedorCopilotoReservaId']
const USOS = [
  {
    titulo: 'Assistente IA (portal)',
    ligado: 'internoAtivo',
    ajuda: 'O assistente que os servidores usam no portal.',
    campos: [{ rotulo: 'Usa', principal: 'provedorInternoId', reserva: 'provedorInternoReservaId' }]
  },
  {
    titulo: 'Atendimento',
    ligado: 'ativo',
    ajuda: 'O robô dos canais (WhatsApp, Instagram, Telegram) e o copiloto dos atendentes.',
    campos: [
      { rotulo: 'Robô', principal: 'provedorAssistenteId', reserva: 'provedorAssistenteReservaId' },
      { rotulo: 'Copiloto', principal: 'provedorCopilotoId', reserva: 'provedorCopilotoReservaId' }
    ]
  }
]
const testando = ref<number>()
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Atd.IaProvedor })
const tipoAtual = computed(() => cfg.value?.tipos.find((t) => t.codigo === form.dados.tipo))
const rotuloTipo = (c: string) => cfg.value?.tipos.find((t) => t.codigo === c)?.rotulo || c

const aplicar = (c: Atd.IaConfig) => {
  cfg.value = c
  CAMPOS_GERAL.forEach((k) => (geral[k] = (c as any)[k] ?? (k.endsWith('tivo') ? false : undefined)))
}

const carregar = async () => {
  loading.value = true
  try {
    aplicar(await Atd.getIaConfig())
  } finally {
    loading.value = false
  }
}

const salvarGeral = async () => {
  try {
    aplicar(await Atd.salvarIaConfig({ ...geral } as any))
    message.success('Configuração gravada')
  } catch {
    await carregar()
  }
}

const abrir = (p?: Atd.IaProvedor) => {
  form.dados = p
    ? { ...p, chave: '' }
    : {
        nome: 'OpenRouter',
        tipo: 'openrouter',
        baseUrl: '',
        modelo: '',
        temperatura: 0.3,
        maxTokens: 800,
        semRaciocinio: false,
        ativo: true,
        prioridade: (cfg.value?.provedores.length || 0) * 10
      }
  form.visivel = true
}

const trocarTipo = () => {
  const t = tipoAtual.value
  if (!t) return
  if (!form.dados.id) {
    form.dados.nome = t.rotulo
    // modelo local, quase sempre sem placa de vídeo: o raciocínio só atrasa
    form.dados.semRaciocinio = t.codigo === 'ollama'
  }
  form.dados.baseUrl = ''
}

const salvar = async () => {
  await formRef.value.validate()
  await Atd.salvarIaProvedor(form.dados)
  message.success('Provedor salvo. Teste a conexão.')
  form.visivel = false
  await carregar()
}

const testar = async (p: Atd.IaProvedor) => {
  testando.value = p.id
  try {
    aplicar(await Atd.testarIaProvedor(p.id!))
    message.success(`${p.nome}: funcionando`)
  } catch {
    await carregar()
  } finally {
    testando.value = undefined
  }
}

const excluir = async (p: Atd.IaProvedor) => {
  await message.delConfirm(`Excluir o provedor ${p.nome}?`)
  await Atd.excluirIaProvedor(p.id!)
  await carregar()
}

onMounted(carregar)
</script>

<style scoped>
.uso {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.uso-topo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
