<template>
  <el-alert
    v-if="resumo && !resumo.podeRevelar"
    class="mb-12px"
    :closable="false"
    show-icon
    title="Para ver senhas, ative a verificação em duas etapas em Meu perfil."
    type="warning"
  />

  <el-row :gutter="12">
    <el-col :lg="7" :span="24">
      <ContentWrap>
        <div class="mb-10px flex items-center justify-between">
          <span class="font-600">Pastas</span>
          <el-button link type="primary" @click="abrirPasta()">
            <Icon class="mr-4px" icon="ep:plus" /> Nova pasta
          </el-button>
        </div>
        <el-empty
          v-if="resumo && !resumo.pastas.length"
          :image-size="60"
          description="Você ainda não está em nenhuma pasta"
        />
        <div
          v-for="p in resumo?.pastas ?? []"
          :key="p.id"
          :class="['pasta', { ativa: pastaId === p.id }]"
          @click="escolherPasta(p)"
        >
          <div class="min-w-0 flex-1">
            <div class="truncate font-500">{{ p.nome }}</div>
            <div class="nota truncate">
              {{ p.meuPapel ? `${p.totalSegredos} senha(s) · ${papel(p.meuPapel)}` : 'Você não é membro' }}
            </div>
          </div>
          <el-button v-if="p.podeGerenciar" link @click.stop="abrirMembros(p)" title="Membros">
            <Icon icon="ep:user" />
          </el-button>
          <el-button v-if="p.podeGerenciar" link @click.stop="abrirPasta(p)" title="Editar pasta">
            <Icon icon="ep:edit" />
          </el-button>
          <el-button v-if="p.podeGerenciar" link @click.stop="apagarPasta(p)" title="Excluir pasta">
            <Icon icon="ep:delete" />
          </el-button>
        </div>
      </ContentWrap>
    </el-col>

    <el-col :lg="17" :span="24">
      <ContentWrap>
        <div class="mb-12px flex flex-wrap items-center gap-8px">
          <el-input
            v-model="busca"
            class="!w-280px"
            clearable
            placeholder="Buscar por nome, usuário ou endereço"
            @clear="carregarSegredos"
            @keyup.enter="carregarSegredos"
          >
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
          <el-button @click="carregarSegredos">Buscar</el-button>
          <el-button v-if="pastaId" link @click="escolherPasta()">Ver todas as pastas</el-button>
          <el-button class="ml-auto" :disabled="!podeCadastrar" plain type="primary" @click="abrirSegredo()">
            <Icon class="mr-4px" icon="ep:plus" /> Nova senha
          </el-button>
        </div>

        <el-empty
          v-if="!loading && !segredos.length"
          :image-size="60"
          description="Nenhuma senha encontrada"
        />
        <el-table v-else v-loading="loading" :data="segredos">
          <el-table-column label="Nome" min-width="170" prop="nome" show-overflow-tooltip />
          <el-table-column label="Usuário" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.usuario">{{ row.usuario }}</span>
              <el-button v-if="row.usuario" link @click="copiar(row.usuario, 'Usuário copiado')">
                <Icon icon="ep:copy-document" />
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="Endereço" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <a v-if="ehLink(row.url)" :href="row.url" rel="noopener noreferrer" target="_blank">
                {{ row.url }}
              </a>
              <span v-else>{{ row.url }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="!pastaId" label="Pasta" min-width="120" prop="pasta" />
          <el-table-column :formatter="dateFormatter" label="Senha trocada em" prop="senhaAlteradaEm" width="165" />
          <el-table-column align="right" fixed="right" label="" width="190">
            <template #default="{ row }">
              <el-button link type="primary" @click="ver(row)">Ver</el-button>
              <el-button v-if="row.podeEditar" link @click="abrirSegredo(row)">Editar</el-button>
              <el-button v-if="row.podeEditar" link type="danger" @click="apagarSegredo(row)">
                Excluir
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-col>
  </el-row>

  <!-- senha aberta: some sozinha e não fica na memória da tela -->
  <el-dialog v-model="aberto.visivel" :title="aberto.nome" width="min(520px, 94vw)" @closed="esquecer">
    <p class="nota mb-8px">Esta consulta fica registrada na trilha de auditoria.</p>
    <el-input :model-value="aberto.senha" readonly>
      <template #append>
        <el-button @click="copiar(aberto.senha, 'Senha copiada. A área de transferência será limpa em 30 s', true)">
          <Icon icon="ep:copy-document" />
        </el-button>
      </template>
    </el-input>
    <template v-if="aberto.notas">
      <div class="mb-4px mt-12px font-500">Notas</div>
      <el-input :model-value="aberto.notas" :rows="4" readonly type="textarea" />
    </template>
    <p class="nota mt-8px">Esta janela fecha sozinha em {{ aberto.restante }} s.</p>
  </el-dialog>

  <el-dialog v-model="formSegredo.visivel" :title="formSegredo.d.id ? 'Editar senha' : 'Nova senha'" width="min(560px, 94vw)">
    <el-form ref="segredoRef" :model="formSegredo.d" :rules="regrasSegredo" label-position="top">
      <el-form-item label="Pasta" prop="pastaId">
        <el-select v-model="formSegredo.d.pastaId" class="w-full">
          <el-option v-for="p in pastasEditaveis" :key="p.id" :label="p.nome" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Nome" prop="nome">
        <el-input v-model="formSegredo.d.nome" maxlength="200" placeholder="Ex.: Firewall da sede" />
      </el-form-item>
      <el-form-item label="Usuário">
        <el-input v-model="formSegredo.d.usuario" autocomplete="off" maxlength="200" />
      </el-form-item>
      <el-form-item label="Endereço">
        <el-input v-model="formSegredo.d.url" maxlength="500" placeholder="https://..." />
      </el-form-item>
      <el-form-item :label="formSegredo.d.id ? 'Nova senha (vazia mantém a atual)' : 'Senha'">
        <el-input
          v-model="formSegredo.d.senha"
          autocomplete="new-password"
          maxlength="4096"
          show-password
          type="password"
        >
          <template #append>
            <el-button @click="formSegredo.d.senha = CofreApi.gerarSenha()">Gerar</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item v-if="!formSegredo.d.id" label="Notas (cifradas)">
        <el-input v-model="formSegredo.d.notas" :rows="3" maxlength="8000" type="textarea" />
      </el-form-item>
      <el-form-item v-else label="Notas (cifradas)">
        <el-checkbox v-model="formSegredo.trocarNotas">Substituir as notas atuais</el-checkbox>
        <el-input
          v-if="formSegredo.trocarNotas"
          v-model="formSegredo.d.notas"
          :rows="3"
          maxlength="8000"
          placeholder="Vazio apaga as notas"
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formSegredo.visivel = false">Cancelar</el-button>
      <el-button :loading="salvando" type="primary" @click="gravarSegredo">Salvar</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="formPasta.visivel" :title="formPasta.id ? 'Editar pasta' : 'Nova pasta'" width="min(460px, 94vw)">
    <el-form label-position="top">
      <el-form-item label="Nome" required>
        <el-input v-model="formPasta.nome" maxlength="120" />
      </el-form-item>
      <el-form-item label="Descrição">
        <el-input v-model="formPasta.descricao" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formPasta.visivel = false">Cancelar</el-button>
      <el-button :disabled="!formPasta.nome.trim()" :loading="salvando" type="primary" @click="gravarPasta">
        Salvar
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="painelMembros.visivel" :title="`Membros: ${painelMembros.pasta?.nome ?? ''}`" width="min(620px, 94vw)">
    <div class="mb-12px flex flex-wrap gap-8px">
      <UsuarioSelect v-model="painelMembros.novoUserId" class="!w-260px" />
      <el-select v-model="painelMembros.novoPapel" class="!w-180px">
        <el-option v-for="o in CofreApi.PAPEIS" :key="o.valor" :label="o.rotulo" :value="o.valor" />
      </el-select>
      <el-button :disabled="!painelMembros.novoUserId" type="primary" @click="incluirMembro">Incluir</el-button>
    </div>
    <el-table :data="painelMembros.lista" size="small">
      <el-table-column label="Pessoa" min-width="180" prop="nome" />
      <el-table-column label="Papel" width="200">
        <template #default="{ row }">
          <el-select :model-value="row.papel" size="small" @change="(v) => mudarPapel(row, v)">
            <el-option v-for="o in CofreApi.PAPEIS" :key="o.valor" :label="o.rotulo" :value="o.valor" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column align="right" label="" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="tirarMembro(row)">Tirar</el-button>
        </template>
      </el-table-column>
    </el-table>
    <p class="nota mt-10px">
      Ao tirar alguém da pasta, troque as senhas que essa pessoa conhecia. A trilha de auditoria mostra quais ela viu.
    </p>
  </el-dialog>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import * as CofreApi from '@/api/cofre'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'CofreSenhas' })

const message = useMessage()
const SEGUNDOS_ABERTA = 60
const SEGUNDOS_AREA_TRANSFERENCIA = 30

const resumo = ref<CofreApi.Resumo>()
const pastaId = ref<number>()
const busca = ref('')
const segredos = ref<CofreApi.Segredo[]>([])
const loading = ref(false)
const salvando = ref(false)
const segredoRef = ref()

const papel = (v?: number) => CofreApi.PAPEIS.find((o) => o.valor === v)?.rotulo ?? ''
// endereço digitado por alguém: só vira link se for http(s), para nunca abrir javascript:
const ehLink = (url?: string) => !!url && /^https?:\/\//i.test(url)
const pastasEditaveis = computed(() => (resumo.value?.pastas ?? []).filter((p) => (p.meuPapel ?? 0) >= 2))
const podeCadastrar = computed(() => pastasEditaveis.value.length > 0)

const carregarResumo = async () => {
  resumo.value = await CofreApi.getResumo()
}

const carregarSegredos = async () => {
  loading.value = true
  try {
    segredos.value = await CofreApi.getSegredos({ pastaId: pastaId.value, busca: busca.value || undefined })
  } finally {
    loading.value = false
  }
}

const escolherPasta = (p?: CofreApi.Pasta) => {
  if (p && !p.meuPapel) {
    message.warning('Você administra o cofre, mas não é membro desta pasta. Inclua-se em Membros para ver as senhas.')
    return
  }
  pastaId.value = p?.id
  carregarSegredos()
}

// ------------------------------------------------------------ ver e copiar

const aberto = reactive({ visivel: false, nome: '', senha: '', notas: '', restante: 0 })
let relogio: ReturnType<typeof setInterval> | undefined

const esquecer = () => {
  if (relogio) clearInterval(relogio)
  relogio = undefined
  aberto.visivel = false
  aberto.senha = ''
  aberto.notas = ''
}

const ver = async (s: CofreApi.Segredo) => {
  const r = await CofreApi.revelarSegredo(s.id)
  aberto.nome = s.nome
  aberto.senha = r.senha
  aberto.notas = r.notas ?? ''
  aberto.restante = SEGUNDOS_ABERTA
  aberto.visivel = true
  if (relogio) clearInterval(relogio)
  relogio = setInterval(() => {
    aberto.restante--
    if (aberto.restante <= 0) esquecer()
  }, 1000)
}

const copiar = async (texto: string, aviso: string, limpar = false) => {
  try {
    await navigator.clipboard.writeText(texto)
  } catch {
    message.error('O navegador não deixou copiar. Selecione o texto e copie à mão.')
    return
  }
  message.success(aviso)
  if (limpar) {
    setTimeout(() => {
      // o navegador recusa quando a aba está sem foco
      navigator.clipboard
        .writeText('')
        .catch(() => message.warning('Não deu para limpar a área de transferência: copie outro texto por cima da senha.'))
    }, SEGUNDOS_AREA_TRANSFERENCIA * 1000)
  }
}

onDeactivated(esquecer)
onBeforeUnmount(esquecer)

// ------------------------------------------------------------ senhas

const formVazio = (): CofreApi.SegredoReq => ({ nome: '', usuario: '', url: '', senha: '', notas: '' })
const formSegredo = reactive({ visivel: false, trocarNotas: false, d: formVazio() })
const regrasSegredo = {
  pastaId: [{ required: true, message: 'Escolha a pasta', trigger: 'change' }],
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }]
}

const abrirSegredo = (s?: CofreApi.Segredo) => {
  formSegredo.trocarNotas = false
  formSegredo.d = s
    ? { id: s.id, pastaId: s.pastaId, nome: s.nome, usuario: s.usuario, url: s.url, senha: '', notas: '' }
    : {
        ...formVazio(),
        pastaId: pastasEditaveis.value.some((p) => p.id === pastaId.value)
          ? pastaId.value
          : pastasEditaveis.value[0]?.id
      }
  formSegredo.visivel = true
}

const gravarSegredo = async () => {
  await segredoRef.value.validate()
  const d = { ...formSegredo.d }
  if (d.id && !formSegredo.trocarNotas) delete d.notas
  salvando.value = true
  try {
    await CofreApi.salvarSegredo(d)
    message.success('Senha salva')
    formSegredo.visivel = false
    formSegredo.d = formVazio()
    await Promise.all([carregarResumo(), carregarSegredos()])
  } finally {
    salvando.value = false
  }
}

const apagarSegredo = async (s: CofreApi.Segredo) => {
  await message.confirm(`Excluir a senha "${s.nome}"? Não dá para desfazer.`)
  await CofreApi.excluirSegredo(s.id)
  message.success('Senha excluída')
  await Promise.all([carregarResumo(), carregarSegredos()])
}

// ------------------------------------------------------------ pastas e membros

const formPasta = reactive({ visivel: false, id: undefined as number | undefined, nome: '', descricao: '' })

const abrirPasta = (p?: CofreApi.Pasta) => {
  formPasta.id = p?.id
  formPasta.nome = p?.nome ?? ''
  formPasta.descricao = p?.descricao ?? ''
  formPasta.visivel = true
}

const gravarPasta = async () => {
  salvando.value = true
  try {
    await CofreApi.salvarPasta({ id: formPasta.id, nome: formPasta.nome, descricao: formPasta.descricao })
    formPasta.visivel = false
    await carregarResumo()
  } finally {
    salvando.value = false
  }
}

const apagarPasta = async (p: CofreApi.Pasta) => {
  await message.confirm(`Excluir a pasta "${p.nome}"? Ela precisa estar vazia.`)
  await CofreApi.excluirPasta(p.id)
  if (pastaId.value === p.id) pastaId.value = undefined
  await Promise.all([carregarResumo(), carregarSegredos()])
}

const painelMembros = reactive({
  visivel: false,
  pasta: undefined as CofreApi.Pasta | undefined,
  lista: [] as CofreApi.Membro[],
  novoUserId: undefined as number | undefined,
  novoPapel: 1
})

const carregarMembros = async () => {
  painelMembros.lista = await CofreApi.getMembros(painelMembros.pasta!.id)
}

const abrirMembros = async (p: CofreApi.Pasta) => {
  painelMembros.pasta = p
  painelMembros.novoUserId = undefined
  painelMembros.novoPapel = 1
  await carregarMembros()
  painelMembros.visivel = true
}

const aposMudarMembros = async () => {
  await Promise.all([carregarMembros(), carregarResumo()])
}

const incluirMembro = async () => {
  await CofreApi.salvarMembro({
    pastaId: painelMembros.pasta!.id,
    userId: painelMembros.novoUserId!,
    papel: painelMembros.novoPapel
  })
  painelMembros.novoUserId = undefined
  await aposMudarMembros()
}

const mudarPapel = async (m: CofreApi.Membro, novo: number) => {
  await CofreApi.salvarMembro({ pastaId: painelMembros.pasta!.id, userId: m.userId, papel: novo })
  await aposMudarMembros()
}

const tirarMembro = async (m: CofreApi.Membro) => {
  await message.confirm(`Tirar ${m.nome} da pasta? Depois, troque as senhas que essa pessoa conhecia.`)
  await CofreApi.removerMembro(painelMembros.pasta!.id, m.userId)
  await aposMudarMembros()
}

onMounted(async () => {
  await carregarResumo()
  await carregarSegredos()
})
</script>

<style scoped>
.pasta {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
}
.pasta:hover {
  background: var(--el-fill-color-light);
}
.pasta.ativa {
  background: var(--el-color-primary-light-9);
}
.nota {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
