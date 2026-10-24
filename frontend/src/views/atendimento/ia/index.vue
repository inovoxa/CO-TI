<template>
  <ContentWrap v-loading="loading">
    <template v-if="cfg">
      <el-form inline>
        <el-form-item label="Inteligência artificial">
          <el-switch v-model="geral.ativo" @change="salvarGeral" />
        </el-form-item>
        <el-form-item label="Assistente usa">
          <el-select
            v-model="geral.provedorAssistenteId"
            class="!w-240px"
            clearable
            placeholder="o primeiro ativo da lista"
            @change="salvarGeral"
          >
            <el-option v-for="p in cfg.provedores" :key="p.id" :label="p.nome" :value="p.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="Copiloto usa">
          <el-select
            v-model="geral.provedorCopilotoId"
            class="!w-240px"
            clearable
            placeholder="o primeiro ativo da lista"
            @change="salvarGeral"
          >
            <el-option v-for="p in cfg.provedores" :key="p.id" :label="p.nome" :value="p.id!" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="dica"
        >Se o provedor principal falhar, os outros ativos são tentados na ordem da prioridade (menor
        primeiro). Regras em docs/specs/atendimento-ia.md.</div
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
          <el-tag
            v-if="row.id === cfg?.provedorAssistenteId"
            class="ml-4px"
            disable-transitions
            size="small"
            >assistente</el-tag
          >
          <el-tag
            v-if="row.id === cfg?.provedorCopilotoId"
            class="ml-4px"
            disable-transitions
            size="small"
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
      </el-form-item>
      <el-form-item label="Modelo" prop="modelo">
        <el-input
          v-model="form.dados.modelo"
          :placeholder="tipoAtual?.exemploModelo"
          maxlength="150"
        />
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
  assistente: 'Assistente',
  sugerir: 'Sugerir resposta',
  resumir: 'Resumir',
  reescrever: 'Reescrever',
  teste: 'Teste'
}

const loading = ref(true)
const cfg = ref<Atd.IaConfig>()
const geral = reactive({
  ativo: false,
  provedorAssistenteId: undefined as number | undefined,
  provedorCopilotoId: undefined as number | undefined
})
const testando = ref<number>()
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Atd.IaProvedor })
const tipoAtual = computed(() => cfg.value?.tipos.find((t) => t.codigo === form.dados.tipo))
const rotuloTipo = (c: string) => cfg.value?.tipos.find((t) => t.codigo === c)?.rotulo || c

const aplicar = (c: Atd.IaConfig) => {
  cfg.value = c
  geral.ativo = c.ativo
  geral.provedorAssistenteId = c.provedorAssistenteId
  geral.provedorCopilotoId = c.provedorCopilotoId
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
    aplicar(await Atd.salvarIaConfig({ ...geral }))
    message.success(geral.ativo ? 'IA ligada' : 'Configuração gravada')
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
        ativo: true,
        prioridade: (cfg.value?.provedores.length || 0) * 10
      }
  form.visivel = true
}

const trocarTipo = () => {
  const t = tipoAtual.value
  if (!t) return
  if (!form.dados.id) form.dados.nome = t.rotulo
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
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
