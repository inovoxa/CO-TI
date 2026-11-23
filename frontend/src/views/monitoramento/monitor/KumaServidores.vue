<!-- Uptime Kuma ao vivo: o Kuma verifica e o CO-TI espelha o estado de cada monitor pelo /metrics -->
<template>
  <Dialog v-model="visivel" :title="titulo" width="960px">
    <!-- lista -->
    <template v-if="modo === 'lista'">
      <p class="mt-0 text-13px text-[var(--el-text-color-secondary)]">
        O Kuma continua verificando os links e serviços. O CO-TI lê o estado de cada monitor no
        <code>/metrics</code> do Kuma e cuida do telão, dos avisos, do chamado automático e do
        histórico. No Kuma, crie a chave em <b>Configurações → Chaves de API</b>.
      </p>
      <el-table :data="lista" size="small">
        <el-table-column label="Kuma" min-width="160" prop="nome" />
        <el-table-column label="Endereço" min-width="240" prop="url" />
        <el-table-column label="Monitores" prop="monitores" width="90" />
        <el-table-column label="" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="abrirSincronizar(row)">Sincronizar</el-button>
            <el-button link type="primary" @click="editar(row)">Editar</el-button>
            <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- cadastro -->
    <el-form
      v-else-if="modo === 'editar'"
      ref="formRef"
      :model="dados"
      :rules="regras"
      label-width="150px"
    >
      <el-form-item label="Nome" prop="nome">
        <el-input v-model="dados.nome" maxlength="100" placeholder="Ex.: Kuma da Prefeitura" />
      </el-form-item>
      <el-form-item label="Endereço" prop="url">
        <el-input
          v-model="dados.url"
          placeholder="https://kuma.orgao.gov.br ou http://10.0.0.5:3001"
        />
      </el-form-item>
      <el-form-item label="Chave de API" prop="chave">
        <el-input
          v-model="dados.chave"
          :placeholder="
            dados.chaveDefinida ? 'guardada (cifrada): deixe em branco para manter' : 'uk1_...'
          "
          show-password
        />
      </el-form-item>
      <el-form-item label="">
        <el-checkbox v-model="loginComum">
          O Kuma não tem chave de API (entrar com usuário e senha)
        </el-checkbox>
      </el-form-item>
      <el-form-item v-if="loginComum" label="Usuário">
        <el-input v-model="dados.usuario" maxlength="100" />
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          A senha vai no campo acima. Com chave de API, deixe desmarcado: o Kuma ignora o usuário.
        </div>
      </el-form-item>
      <el-form-item v-if="/^https:/i.test(dados.url || '')" label="">
        <el-checkbox v-model="dados.ignorarTls">
          Ignorar erros de TLS (certificado autoassinado na rede interna)
        </el-checkbox>
      </el-form-item>
      <el-alert
        v-if="resultado"
        :closable="false"
        :title="resultado"
        class="mb-8px"
        show-icon
        type="success"
      />
    </el-form>

    <!-- sincronizar: prévia do que entra, muda e sai -->
    <template v-else>
      <div class="mb-8px flex flex-wrap items-center gap-8px text-13px">
        <el-tag disable-transitions type="success">{{ contagem.novo }} novos</el-tag>
        <el-tag disable-transitions type="warning">{{ contagem.muda }} mudaram no Kuma</el-tag>
        <el-tag disable-transitions type="info">{{ contagem.igual }} já acompanhados</el-tag>
        <el-tag disable-transitions type="danger">{{ contagem.sumiu }} sumiram do Kuma</el-tag>
        <el-input v-model="busca" class="!w-220px ml-auto" clearable placeholder="Buscar" />
      </div>
      <el-table
        ref="tabela"
        v-loading="carregando"
        :data="filtrados"
        max-height="400"
        row-key="chaveLinha"
        size="small"
        @selection-change="(l: Linha[]) => (escolhidos = l)"
      >
        <el-table-column
          :reserve-selection="true"
          :selectable="(r: Linha) => r.situacao !== 'igual'"
          type="selection"
          width="40"
        />
        <el-table-column label="Monitor no Kuma" min-width="220">
          <template #default="{ row }">
            <div class="font-medium">{{ row.nome }}</div>
            <div class="break-all text-12px text-[var(--el-text-color-secondary)]">
              {{ row.alvo }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Tipo lá" prop="tipoKuma" width="90" />
        <el-table-column label="Agora" width="110">
          <template #default="{ row }">
            <el-tag
              v-if="row.estado !== undefined && row.estado !== null && ESTADOS[row.estado]"
              :type="ESTADOS[row.estado].cor"
              disable-transitions
              size="small"
              >{{ ESTADOS[row.estado].rotulo }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="O que acontece" min-width="260">
          <template #default="{ row }">
            <span v-if="row.situacao === 'novo'">passa a ser acompanhado</span>
            <span v-else-if="row.situacao === 'igual'" class="text-[var(--el-text-color-secondary)]"
              >já acompanhado ({{ row.monitorNome }})</span
            >
            <span v-else-if="row.situacao === 'muda'" class="text-[var(--el-color-warning)]">
              atualiza {{ row.mudanca }}
            </span>
            <span v-else class="text-[var(--el-color-danger)]">
              {{ row.mudanca }} — marcado, o monitor do CO-TI é excluído
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-form class="mt-12px" label-width="190px">
        <el-form-item label="Unidade dos novos (opcional)">
          <el-select
            v-model="opcoes.unidadeId"
            class="!w-300px"
            clearable
            filterable
            placeholder="definir depois, um a um"
          >
            <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="Ler o estado a cada">
          <el-input-number
            v-model="opcoes.intervaloS"
            :max="3600"
            :min="15"
            controls-position="right"
          />
          <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">segundos</span>
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="opcoes.abrirChamado">
            Abrir chamado quando o Kuma disser que caiu (vale para os novos)
          </el-checkbox>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="feito"
        :closable="false"
        :title="feito"
        class="mb-8px"
        show-icon
        type="success"
      />
    </template>

    <template #footer>
      <template v-if="modo === 'editar'">
        <el-button :loading="testando" @click="testar">Testar conexão</el-button>
        <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
        <el-button @click="voltar">Voltar</el-button>
      </template>
      <template v-else-if="modo === 'sincronizar'">
        <el-button
          :disabled="!escolhidos.length"
          :loading="salvando"
          type="primary"
          @click="sincronizar"
        >
          Aplicar {{ escolhidos.length }}
        </el-button>
        <el-button @click="voltar">Voltar</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="editar()">Novo Kuma</el-button>
        <el-button @click="visivel = false">Fechar</el-button>
      </template>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import { ESTADOS } from '@/api/monitoramento'

defineOptions({ name: 'MonitoramentoKumaServidores' })
const emit = defineEmits<{ mudou: [] }>()
const message = useMessage()

type Linha = Mon.ItemAoVivo & { chaveLinha: string }

const visivel = ref(false)
const modo = ref<'lista' | 'editar' | 'sincronizar'>('lista')
const salvando = ref(false)
const testando = ref(false)
const carregando = ref(false)
const resultado = ref('')
const feito = ref('')
const lista = ref<Mon.KumaServidor[]>([])
const dados = ref<Mon.KumaServidor>({ nome: '', url: '' })
const loginComum = ref(false)
const formRef = ref()
const servidor = ref<Mon.KumaServidor>()
const itens = ref<Linha[]>([])
const escolhidos = ref<Linha[]>([])
const busca = ref('')
const tabela = ref()
const unidades = ref<Mon.Unidade[]>([])
const opcoes = reactive({
  unidadeId: undefined as number | undefined,
  intervaloS: 30,
  abrirChamado: false
})

const titulo = computed(() =>
  modo.value === 'sincronizar'
    ? `Sincronizar com ${servidor.value?.nome ?? 'o Kuma'}`
    : 'Uptime Kuma ao vivo'
)
const regras = computed(() => ({
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  url: [{ required: true, message: 'Informe o endereço', trigger: 'blur' }],
  chave: dados.value.chaveDefinida
    ? []
    : [{ required: true, message: 'Informe a chave de API (ou a senha)', trigger: 'blur' }]
}))
const contagem = computed(() => {
  const c = { novo: 0, igual: 0, muda: 0, sumiu: 0 }
  itens.value.forEach((i) => c[i.situacao]++)
  return c
})
const filtrados = computed(() => {
  const b = busca.value.trim().toLowerCase()
  return b
    ? itens.value.filter((i) => `${i.nome ?? ''} ${i.alvo ?? ''}`.toLowerCase().includes(b))
    : itens.value
})

const carregar = async () => {
  lista.value = await Mon.getKumaServidores()
}
const abrir = async () => {
  modo.value = 'lista'
  await carregar()
  visivel.value = true
}
defineExpose({ abrir })

const voltar = async () => {
  modo.value = 'lista'
  await carregar()
}
const editar = (s?: Mon.KumaServidor) => {
  dados.value = s ? { ...s, chave: '' } : { nome: '', url: 'https://', ignorarTls: false }
  loginComum.value = !!s?.usuario
  resultado.value = ''
  modo.value = 'editar'
}
const pedido = (): Mon.KumaServidor => ({
  ...dados.value,
  usuario: loginComum.value ? dados.value.usuario : ''
})
const testar = async () => {
  await formRef.value.validate()
  testando.value = true
  try {
    resultado.value = `Conectou: ${await Mon.testarKumaServidor(pedido())}`
  } finally {
    testando.value = false
  }
}
const salvar = async () => {
  await formRef.value.validate()
  salvando.value = true
  try {
    const id = await Mon.salvarKumaServidor(pedido())
    message.success('Kuma salvo')
    emit('mudou')
    await carregar()
    const salvo = lista.value.find((s) => s.id === id)
    // recém-cadastrado: já mostra o que dá para acompanhar
    if (salvo && !dados.value.id) await abrirSincronizar(salvo)
    else modo.value = 'lista'
  } finally {
    salvando.value = false
  }
}
const excluir = async (s: Mon.KumaServidor) => {
  await message.delConfirm(`Excluir o Kuma "${s.nome}"?`)
  await Mon.excluirKumaServidor(s.id!)
  await carregar()
  emit('mudou')
}

const abrirSincronizar = async (s: Mon.KumaServidor) => {
  servidor.value = s
  feito.value = ''
  busca.value = ''
  itens.value = []
  escolhidos.value = []
  modo.value = 'sincronizar'
  carregando.value = true
  try {
    const [p, u] = await Promise.all([Mon.previaKuma(s.id!), Mon.getUnidades()])
    unidades.value = u
    itens.value = p.itens.map((i) => ({
      ...i,
      chaveLinha: `${i.situacao}:${i.monitorId ?? ''}:${i.chave}`
    }))
    await nextTick()
    tabela.value?.clearSelection()
    // o que sumiu do Kuma só sai se a pessoa marcar
    itens.value
      .filter((i) => i.situacao === 'novo' || i.situacao === 'muda')
      .forEach((i) => tabela.value?.toggleRowSelection(i, true))
  } catch (e) {
    modo.value = 'lista'
    throw e
  } finally {
    carregando.value = false
  }
}
const sincronizar = async () => {
  const remover = escolhidos.value.filter((i) => i.situacao === 'sumiu')
  if (remover.length) {
    await message.confirm(
      `Excluir ${remover.length} monitor(es) do CO-TI que não estão mais no Kuma, com o histórico deles?`
    )
  }
  salvando.value = true
  try {
    const r = await Mon.sincronizarKuma({
      servidorId: servidor.value!.id!,
      adicionar: escolhidos.value.filter((i) => i.situacao === 'novo').map((i) => i.chave),
      atualizar: escolhidos.value.filter((i) => i.situacao === 'muda').map((i) => i.monitorId!),
      remover: remover.map((i) => i.monitorId!),
      ...opcoes
    })
    emit('mudou')
    await abrirSincronizar(servidor.value!)
    feito.value = `${r.adicionados} passaram a ser acompanhados, ${r.atualizados} atualizados e ${r.removidos} excluídos. Em cada monitor, escolha a unidade e o papel no telão.`
  } finally {
    salvando.value = false
  }
}
</script>
