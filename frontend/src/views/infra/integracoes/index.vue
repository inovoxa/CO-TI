<template>
  <ContentWrap>
    <el-tabs v-model="aba">
      <el-tab-pane label="Entradas" name="entradas">
        <div class="mb-12px flex items-center justify-between">
          <p class="ajuda">
            Um sistema externo avisa o CO-TI, que identifica a pessoa, abre o chamado de desativação de usuário, bloqueia no AD e
            encerra. Toda entrada nasce em simulação.
          </p>
          <el-button v-hasPermi="['infra:integracao:update']" plain type="primary" @click="abrir()">
            <Icon class="mr-5px" icon="ep:plus" /> Nova entrada
          </el-button>
        </div>
        <el-table v-loading="carregando" :data="entradas">
          <el-table-column label="Nome" min-width="180" prop="nome" />
          <el-table-column label="Endereço" min-width="300">
            <template #default="{ row }"><code class="break-all">{{ endereco(row) }}</code></template>
          </el-table-column>
          <el-table-column label="Modo" width="170">
            <template #default="{ row }">
              <el-tag :type="MODOS[row.modo]?.cor" disable-transitions>{{ MODOS[row.modo]?.rotulo }}</el-tag>
              <el-tag v-if="!row.ativa" class="ml-4px" disable-transitions type="info">Inativa</el-tag>
            </template>
          </el-table-column>
          <el-table-column align="center" label="" width="230">
            <template #default="{ row }">
              <el-button v-hasPermi="['infra:integracao:update']" link type="primary" @click="abrirTeste(row)">Testar</el-button>
              <el-button v-hasPermi="['infra:integracao:update']" link type="primary" @click="abrir(row)">Editar</el-button>
              <el-button v-hasPermi="['infra:integracao:update']" link type="warning" @click="trocarSegredo(row)">Novo segredo</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Avisos recebidos" name="eventos">
        <div class="mb-12px flex items-center gap-12px">
          <el-select v-model="filtro.status" class="!w-220px" clearable placeholder="Todas as situações" @change="buscarEventos">
            <el-option v-for="(s, k) in SITUACOES" :key="k" :label="s.rotulo" :value="k" />
          </el-select>
          <el-button @click="carregarEventos"><Icon class="mr-4px" icon="ep:refresh" /> Atualizar</el-button>
        </div>
        <el-table :data="eventos">
          <el-table-column label="Recebido em" width="170">
            <template #default="{ row }">{{ dataHora(row.recebidoEm) }}</template>
          </el-table-column>
          <el-table-column label="Entrada" min-width="140">
            <template #default="{ row }">{{ entradas.find((e) => e.id === row.entradaId)?.nome || row.entradaId }}</template>
          </el-table-column>
          <el-table-column label="Situação" width="150">
            <template #default="{ row }">
              <el-tag :type="SITUACOES[row.status]?.cor" disable-transitions>{{ SITUACOES[row.status]?.rotulo || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Conta" width="150" prop="login" />
          <el-table-column label="Chamado" width="100">
            <template #default="{ row }">
              <router-link v-if="row.chamadoId" :to="{ path: '/servicedesk/chamados', query: { id: String(row.chamadoId) } }">#{{ row.chamadoId }}</router-link>
            </template>
          </el-table-column>
          <el-table-column label="Resultado" min-width="320" prop="resultado" />
          <el-table-column label="Origem" width="130" prop="ip" />
        </el-table>
        <Pagination v-model:limit="filtro.pageSize" v-model:page="filtro.pageNo" :total="totalEventos" @pagination="carregarEventos" />
      </el-tab-pane>

      <el-tab-pane label="Chaves de API" name="chaves">
        <div class="mb-12px flex items-center justify-between">
          <p class="ajuda">
            Outros sistemas abrem e consultam chamados em <code>/admin-api/integracao/v1/chamados</code>, com
            <code>Authorization: Bearer</code> + chave. Valem a validação e a aprovação normais, e formulários com operação no AD ficam
            de fora.
          </p>
          <el-button v-hasPermi="['infra:integracao:update']" plain type="primary" @click="abrirChave">
            <Icon class="mr-5px" icon="ep:plus" /> Nova chave
          </el-button>
        </div>
        <el-table :data="chaves">
          <el-table-column label="Nome" min-width="180" prop="nome" />
          <el-table-column label="Início da chave" width="150"><template #default="{ row }"><code>{{ row.prefixo }}…</code></template></el-table-column>
          <el-table-column label="Pode" min-width="220"><template #default="{ row }">{{ row.escopos.map((e) => ESCOPOS[e]).join(', ') }}</template></el-table-column>
          <el-table-column label="Último uso" width="170"><template #default="{ row }">{{ dataHora(row.ultimoUso) }}</template></el-table-column>
          <el-table-column label="Situação" width="110">
            <template #default="{ row }"><el-tag :type="row.ativa ? 'success' : 'info'" disable-transitions>{{ row.ativa ? 'Ativa' : 'Revogada' }}</el-tag></template>
          </el-table-column>
          <el-table-column align="center" label="" width="100">
            <template #default="{ row }">
              <el-button v-if="row.ativa" v-hasPermi="['infra:integracao:update']" link type="danger" @click="revogar(row)">Revogar</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Webhooks de saída" name="saidas">
        <div class="mb-12px flex items-center justify-between">
          <p class="ajuda">
            Avisa outro sistema quando um chamado muda, com a mesma assinatura das entradas. O aviso traz só o número do chamado e a
            situação.
          </p>
          <el-button v-hasPermi="['infra:integracao:update']" plain type="primary" @click="abrirSaida()">
            <Icon class="mr-5px" icon="ep:plus" /> Novo webhook
          </el-button>
        </div>
        <el-table :data="saidas">
          <el-table-column label="Nome" min-width="160" prop="nome" />
          <el-table-column label="Destino" min-width="280" prop="url" />
          <el-table-column label="Eventos" min-width="240"><template #default="{ row }">{{ row.eventos.map((e) => EVENTOS[e]).join(', ') }}</template></el-table-column>
          <el-table-column label="Situação" width="100">
            <template #default="{ row }"><el-tag :type="row.ativa ? 'success' : 'info'" disable-transitions>{{ row.ativa ? 'Ativo' : 'Inativo' }}</el-tag></template>
          </el-table-column>
          <el-table-column align="center" label="" width="90">
            <template #default="{ row }"><el-button v-hasPermi="['infra:integracao:update']" link type="primary" @click="abrirSaida(row)">Editar</el-button></template>
          </el-table-column>
        </el-table>
        <h4 class="mb-8px mt-20px">Últimas entregas</h4>
        <el-table :data="entregas">
          <el-table-column label="Criada em" width="170"><template #default="{ row }">{{ dataHora(row.criadoEm) }}</template></el-table-column>
          <el-table-column label="Webhook" min-width="150" prop="saida" />
          <el-table-column label="Evento" width="130"><template #default="{ row }">{{ EVENTOS[row.evento] }}</template></el-table-column>
          <el-table-column label="Chamado" width="90"><template #default="{ row }">#{{ row.chamadoId }}</template></el-table-column>
          <el-table-column label="Situação" width="120">
            <template #default="{ row }"><el-tag :type="SITUACOES_ENTREGA[row.status]?.cor" disable-transitions>{{ SITUACOES_ENTREGA[row.status]?.rotulo }}</el-tag></template>
          </el-table-column>
          <el-table-column align="right" label="Tentativas" width="100" prop="tentativas" />
          <el-table-column label="Erro" min-width="220" prop="erro" />
        </el-table>
        <Pagination v-model:limit="filtroEntregas.pageSize" v-model:page="filtroEntregas.pageNo" :total="totalEntregas" @pagination="carregarSaidas" />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <Dialog v-model="dialogo" :title="form.id ? 'Editar entrada' : 'Nova entrada'" width="760px">
    <el-form label-position="top">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="Nome" required><el-input v-model="form.nome" placeholder="RH: desligamentos" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Código (vai no endereço)" required>
            <el-input v-model="form.codigo" :disabled="!!form.id" placeholder="rh-desligamento" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Modo">
            <el-select v-if="form.id" v-model="form.modo">
              <el-option v-for="(m, k) in MODOS" :key="k" :label="m.rotulo" :value="k" />
            </el-select>
            <span v-else class="ajuda">Simulação. Mude depois de conferir os avisos recebidos.</span>
            <div v-if="form.id" class="ajuda">{{ MODOS[form.modo!]?.ajuda }}</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Situação"><el-switch v-model="form.ativa" active-text="Ativa" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Autenticação">
            <el-select v-model="form.autenticacao">
              <el-option label="Assinatura HMAC (recomendado)" value="hmac" />
              <el-option label="Token fixo, exige lista de IPs" value="bearer" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="IPs permitidos"><el-input v-model="form.ipsPermitidos" placeholder="10.0.5.20, 10.0.6.0/24" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Formulário" required>
            <el-select v-model="form.formularioId" filterable>
              <el-option v-for="f in formularios" :key="f.id" :label="`${f.codigo || ''} ${f.nome}`" :value="f.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Chamado aberto em nome de" required>
            <el-select v-model="form.solicitanteId" filterable>
              <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">Como ler o aviso</el-divider>
      <p class="ajuda mb-12px">Informe onde cada dado está no JSON, com pontos: <code>funcionario.matricula</code>.</p>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="Identificador da pessoa" required><el-input v-model="form.caminhoId" placeholder="funcionario.matricula" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Atributo procurado no AD">
            <el-select v-model="form.atributoAd"><el-option v-for="a in ATRIBUTOS" :key="a" :label="a" :value="a" /></el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Situação" required><el-input v-model="form.caminhoSituacao" placeholder="situacao" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Valores que pedem bloqueio" required><el-input v-model="form.valoresDesligar" placeholder="DESLIGADO, BLOQUEADO" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Valores que cancelam um bloqueio pendente"><el-input v-model="form.valoresCancelar" placeholder="READMITIDO, CANCELADO" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Data de efetivação (se futura, agenda)"><el-input v-model="form.caminhoData" placeholder="data_desligamento" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Nome da pessoa"><el-input v-model="form.caminhoNome" placeholder="funcionario.nome" /></el-form-item>
          <el-checkbox v-model="form.conferirNome">Nome diferente do AD vai para a equipe</el-checkbox>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Id do evento na origem"><el-input v-model="form.caminhoChave" placeholder="vazio = o próprio conteúdo" /></el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">Campos do chamado (opcionais)</el-divider>
      <el-row :gutter="16">
        <el-col v-for="c in CAMPOS" :key="c.campo" :span="12">
          <el-form-item :label="c.rotulo"><el-input v-model="form.mapa![c.campo]" /></el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">Travas</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="Bloqueios automáticos por dia"><el-input-number v-model="form.limiteDia" :min="1" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Logins protegidos"><el-input v-model="form.loginsProtegidos" placeholder="svc.backup, svc.scan" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Grupos protegidos (separados por ponto e vírgula)">
            <el-input v-model="form.gruposProtegidos" placeholder="Domain Admins; Enterprise Admins" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="dialogo = false">Cancelar</el-button>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="teste.visivel" :title="`Testar: ${teste.entrada?.nome}`" width="640px">
    <p class="ajuda mb-8px">Cole um JSON de exemplo. Nada é gravado, nenhum chamado é aberto e o AD só é consultado.</p>
    <el-input v-model="teste.json" :rows="8" type="textarea" />
    <el-alert v-if="teste.resultado" :closable="false" :title="teste.resultado.mensagem" :type="CORES_TESTE[teste.resultado.tipo]" class="mt-12px" show-icon />
    <el-descriptions v-if="teste.resultado" :column="1" border class="mt-12px" size="small">
      <el-descriptions-item label="Conta">{{ teste.resultado.login || '—' }}</el-descriptions-item>
      <el-descriptions-item v-if="teste.resultado.executarEm" label="Agendado para">{{ dataHora(teste.resultado.executarEm) }}</el-descriptions-item>
      <el-descriptions-item v-for="(v, k) in teste.resultado.campos" :key="k" :label="String(k)">{{ v }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="teste.visivel = false">Fechar</el-button>
      <el-button type="primary" @click="testar">Testar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="chave.visivel" title="Nova chave de API" width="560px">
    <el-form label-position="top">
      <el-form-item label="Nome" required><el-input v-model="chave.form.nome" placeholder="Sistema de protocolo" /></el-form-item>
      <el-form-item label="O que pode fazer" required>
        <el-checkbox-group v-model="chave.form.escopos">
          <el-checkbox v-for="(r, k) in ESCOPOS" :key="k" :label="r" :value="k" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="Chamados abertos em nome de" required>
        <el-select v-model="chave.form.solicitanteId" filterable>
          <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="IPs permitidos"><el-input v-model="chave.form.ipsPermitidos" placeholder="10.0.5.20, 10.0.6.0/24" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="chave.visivel = false">Cancelar</el-button>
      <el-button type="primary" @click="criarChave">Criar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="saida.visivel" :title="saida.form.id ? 'Editar webhook' : 'Novo webhook'" width="560px">
    <el-form label-position="top">
      <el-form-item label="Nome" required><el-input v-model="saida.form.nome" /></el-form-item>
      <el-form-item label="Destino" required><el-input v-model="saida.form.url" placeholder="https://sistema.orgao.gov.br/avisos" /></el-form-item>
      <el-form-item label="Eventos" required>
        <el-checkbox-group v-model="saida.form.eventos">
          <el-checkbox v-for="(r, k) in EVENTOS" :key="k" :label="r" :value="k" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item v-if="saida.form.id" label="Situação"><el-switch v-model="saida.form.ativa" active-text="Ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="saida.visivel = false">Cancelar</el-button>
      <el-button type="primary" @click="salvarSaida">Salvar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="segredo.visivel" title="Guarde o segredo agora" width="620px">
    <el-alert :closable="false" class="mb-12px" title="Ele não será mostrado de novo." type="warning" show-icon />
    <code class="break-all">{{ segredo.valor }}</code>
    <p class="ajuda mt-12px">
      Assinatura: envie <code>X-COTI-Timestamp</code> (segundos desde 1970) e <code>X-COTI-Signature</code>, o HMAC-SHA256 em
      hexadecimal de <code>timestamp.corpo</code>. Token fixo: <code>Authorization: Bearer</code> + segredo.
    </p>
    <template #footer><el-button type="primary" @click="segredo.visivel = false">Já guardei</el-button></template>
  </Dialog>
</template>

<script lang="ts" setup>
import request from '@/config/axios'
import * as ItsmApi from '@/api/itsm'
import { getSimpleUserList, UserVO } from '@/api/system/user'

defineOptions({ name: 'InfraIntegracoes' })

const message = useMessage()

const MODOS: Record<string, { rotulo: string; cor: any; ajuda: string }> = {
  simulacao: { rotulo: 'Simulação', cor: 'info', ajuda: 'Só registra o que seria feito.' },
  confirmacao: { rotulo: 'Com confirmação', cor: 'warning', ajuda: 'Abre o chamado e um técnico confirma o bloqueio em Execuções no AD.' },
  automatico: { rotulo: 'Automático', cor: 'success', ajuda: 'Abre o chamado, bloqueia no AD e encerra sem intervenção.' }
}
const SITUACOES: Record<string, { rotulo: string; cor: any }> = {
  recebido: { rotulo: 'Na fila', cor: 'info' },
  processando: { rotulo: 'Processando', cor: 'info' },
  simulado: { rotulo: 'Simulado', cor: 'info' },
  ignorado: { rotulo: 'Ignorado', cor: 'info' },
  chamado: { rotulo: 'Com a equipe', cor: 'warning' },
  executando: { rotulo: 'Bloqueio pedido', cor: 'success' },
  agendado: { rotulo: 'Agendado', cor: 'primary' },
  confirmacao: { rotulo: 'Aguarda confirmação', cor: 'warning' },
  cancelado: { rotulo: 'Cancelado', cor: 'info' },
  erro: { rotulo: 'Erro', cor: 'danger' }
}
const ESCOPOS: Record<string, string> = { 'chamado:criar': 'Abrir chamados', 'chamado:consultar': 'Consultar os chamados que abriu' }
const EVENTOS: Record<string, string> = {
  'chamado.criado': 'Aberto',
  'chamado.atribuido': 'Atribuído',
  'chamado.solucionado': 'Solucionado',
  'chamado.fechado': 'Fechado',
  'chamado.reaberto': 'Reaberto'
}
const SITUACOES_ENTREGA: Record<string, { rotulo: string; cor: any }> = {
  pendente: { rotulo: 'Na fila', cor: 'info' },
  enviando: { rotulo: 'Enviando', cor: 'info' },
  enviado: { rotulo: 'Entregue', cor: 'success' },
  erro: { rotulo: 'Erro', cor: 'danger' }
}
const CORES_TESTE: Record<string, any> = { EXECUTAR: 'success', CONFIRMAR: 'warning', CHAMADO: 'warning', CANCELAR: 'info', IGNORAR: 'info' }
const ATRIBUTOS = ['employeeID', 'employeeNumber', 'mail', 'sAMAccountName', 'userPrincipalName']
const CAMPOS = [
  { campo: 'nome_completo', rotulo: 'Nome completo' },
  { campo: 'matricula', rotulo: 'Matrícula' },
  { campo: 'secretaria', rotulo: 'Secretaria' },
  { campo: 'coordenadoria', rotulo: 'Coordenadoria' },
  { campo: 'unidade', rotulo: 'Unidade' },
  { campo: 'motivo_desativacao', rotulo: 'Motivo' },
  { campo: 'numero_processo', rotulo: 'Número do processo' }
]

const aba = ref('entradas')
const carregando = ref(false)
const salvando = ref(false)
const dialogo = ref(false)
const entradas = ref<any[]>([])
const formularios = ref<ItsmApi.Formulario[]>([])
const usuarios = ref<UserVO[]>([])
const eventos = ref<any[]>([])
const totalEventos = ref(0)
const filtro = reactive({ pageNo: 1, pageSize: 20, status: undefined as string | undefined })
const form = ref<any>({})
const teste = reactive<any>({ visivel: false, entrada: null, json: '', resultado: null })
const segredo = reactive({ visivel: false, valor: '' })

const chaves = ref<any[]>([])
const saidas = ref<any[]>([])
const entregas = ref<any[]>([])
const totalEntregas = ref(0)
const filtroEntregas = reactive({ pageNo: 1, pageSize: 20 })
const chave = reactive<any>({ visivel: false, form: {} })
const saida = reactive<any>({ visivel: false, form: {} })

const dataHora = (d: any) => (d ? new Date(d).toLocaleString('pt-BR') : '—')
const endereco = (e: any) => `${location.origin}/admin-api/integracao/entrada/${e.codigo}`

const carregar = async () => {
  carregando.value = true
  try {
    entradas.value = await request.get({ url: '/integracao/entradas' })
  } finally {
    carregando.value = false
  }
}
const carregarEventos = async () => {
  const d = await request.get({ url: '/integracao/eventos', params: filtro })
  eventos.value = d.list
  totalEventos.value = d.total
}
const buscarEventos = () => {
  filtro.pageNo = 1
  carregarEventos()
}

const abrir = async (e?: any) => {
  if (!formularios.value.length) {
    const [f, u] = await Promise.all([ItsmApi.getFormularios(), getSimpleUserList()])
    formularios.value = f.filter((x) => x.acaoAd === 'Desativar')
    usuarios.value = u
  }
  form.value = e
    ? { ...e, mapa: { ...(e.mapa || {}) } }
    : { ativa: true, autenticacao: 'hmac', atributoAd: 'employeeID', limiteDia: 5, conferirNome: false, mapa: {}, formularioId: formularios.value[0]?.id, gruposProtegidos: 'Domain Admins; Enterprise Admins' }
  dialogo.value = true
}

const salvar = async () => {
  salvando.value = true
  try {
    const r = await request.post({ url: '/integracao/entradas', data: form.value })
    dialogo.value = false
    message.success('Salvo')
    if (r.segredo) {
      segredo.valor = r.segredo
      segredo.visivel = true
    }
    await carregar()
  } finally {
    salvando.value = false
  }
}

const trocarSegredo = async (e: any) => {
  await message.confirm(`Trocar o segredo de "${e.nome}"? O atual deixa de valer na hora.`)
  segredo.valor = await request.post({ url: '/integracao/entradas/segredo', params: { id: e.id } })
  segredo.visivel = true
}

const abrirTeste = (e: any) => {
  teste.entrada = e
  teste.resultado = null
  teste.visivel = true
}
const testar = async () => {
  teste.resultado = await request.post({ url: '/integracao/entradas/testar', data: { id: teste.entrada.id, json: teste.json } })
}

const carregarChaves = async () => {
  chaves.value = await request.get({ url: '/integracao/chaves' })
}
const carregarSaidas = async () => {
  const [s, e] = await Promise.all([
    request.get({ url: '/integracao/saidas' }),
    request.get({ url: '/integracao/entregas', params: filtroEntregas })
  ])
  saidas.value = s
  entregas.value = e.list
  totalEntregas.value = e.total
}
const abrirChave = async () => {
  if (!usuarios.value.length) usuarios.value = await getSimpleUserList()
  chave.form = { escopos: ['chamado:criar', 'chamado:consultar'] }
  chave.visivel = true
}
const criarChave = async () => {
  segredo.valor = await request.post({ url: '/integracao/chaves', data: chave.form })
  chave.visivel = false
  segredo.visivel = true
  await carregarChaves()
}
const revogar = async (c: any) => {
  await message.confirm(`Revogar a chave "${c.nome}"? O sistema que a usa para de funcionar na hora.`)
  await request.post({ url: '/integracao/chaves/revogar', params: { id: c.id } })
  await carregarChaves()
}
const abrirSaida = (s?: any) => {
  saida.form = s ? { ...s } : { eventos: ['chamado.fechado'], ativa: true }
  saida.visivel = true
}
const salvarSaida = async () => {
  const r = await request.post({ url: '/integracao/saidas', data: saida.form })
  saida.visivel = false
  if (r.segredo) {
    segredo.valor = r.segredo
    segredo.visivel = true
  }
  await carregarSaidas()
}

watch(aba, (a) => {
  if (a === 'eventos') carregarEventos()
  if (a === 'chaves') carregarChaves()
  if (a === 'saidas') carregarSaidas()
})
onMounted(carregar)
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
