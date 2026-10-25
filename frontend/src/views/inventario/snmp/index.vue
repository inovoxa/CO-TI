<template>
  <ContentWrap v-if="pode" title="Credenciais">
    <template #header>
      <el-button plain type="primary" @click="abrirCredencial()">
        <Icon class="mr-5px" icon="ep:plus" /> Nova credencial
      </el-button>
    </template>
    <el-table :data="credenciais" size="small">
      <el-table-column label="Ordem" prop="ordem" width="70" />
      <el-table-column label="Nome" min-width="160" prop="nome" />
      <el-table-column label="Versão" width="80" prop="versao" />
      <el-table-column label="Detalhe" min-width="220">
        <template #default="{ row }">
          <template v-if="row.versao === 'v2c'">comunidade guardada (cifrada)</template>
          <template v-else
            >usuário {{ row.usuario }} · {{ row.nivel }}
            <template v-if="row.nivel !== 'noAuthNoPriv'"> · {{ row.authProtocolo }}</template>
            <template v-if="row.nivel === 'authPriv'"> / {{ row.privProtocolo }}</template>
          </template>
          <span v-if="row.porta !== 161"> · porta {{ row.porta }}</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="Ativa" width="70">
        <template #default="{ row }">
          <Icon :icon="row.ativo ? 'ep:check' : 'ep:close'" />
        </template>
      </el-table-column>
      <el-table-column width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrirCredencial(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
      As credenciais são tentadas em ordem em cada IP; a que responder fica lembrada para aquele IP.
      Os segredos ficam cifrados e nunca são mostrados de volta.
    </div>
  </ContentWrap>

  <ContentWrap v-if="pode" title="Consultar agora">
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="ip"
          class="!w-200px"
          placeholder="IP (ex.: 10.20.5.50)"
          @keyup.enter="consultar"
        />
      </el-form-item>
      <el-form-item>
        <el-button :disabled="!ip.trim()" :loading="consultando" @click="consultar">
          Consultar IP
        </el-button>
        <el-button :loading="varrendo" type="primary" @click="varrer">
          <Icon class="mr-5px" icon="ep:refresh" /> Varrer todos os alvos
        </el-button>
      </el-form-item>
    </el-form>
    <el-alert
      v-if="resultado"
      :closable="false"
      :type="resultado.respondeu ? 'success' : 'error'"
      class="mt-14px"
      show-icon
    >
      <template #title>
        <template v-if="resultado.respondeu">
          {{ resultado.ip }} respondeu ({{ resultado.credencial }}):
          {{ rotulo(TIPOS_ATIVO, resultado.tipo) }} {{ resultado.nome }}
        </template>
        <template v-else>{{ resultado.ip }}: {{ resultado.erro }}</template>
      </template>
      <div v-if="resultado.respondeu" class="text-13px">
        {{ [resultado.fabricante, resultado.modelo].filter(Boolean).join(' · ') }}
        <span v-if="resultado.numeroSerie"> · série {{ resultado.numeroSerie }}</span>
        <span v-if="resultado.paginas">
          · {{ resultado.paginas.toLocaleString('pt-BR') }} páginas</span
        >
        <span v-if="resultado.interfaces"> · {{ resultado.interfaces }} interfaces</span>
        <div v-if="resultado.suprimentos?.length">{{ resultado.suprimentos.join(' · ') }}</div>
        <el-link
          v-if="resultado.ativoId"
          type="primary"
          @click="router.push({ path: '/inventario/ativos', query: { id: resultado.ativoId } })"
          >Abrir o ativo</el-link
        >
      </div>
    </el-alert>
  </ContentWrap>

  <ContentWrap title="IPs consultados">
    <el-table v-loading="loading" :data="alvos" size="small">
      <el-table-column label="IP" prop="ip" width="140" />
      <el-table-column label="Ativo" min-width="200">
        <template #default="{ row }">
          <el-link
            v-if="row.ativoId"
            type="primary"
            @click="router.push({ path: '/inventario/ativos', query: { id: row.ativoId } })"
            >{{ row.ativoNome }}</el-link
          >
          <span v-else>—</span>
          <span
            v-if="row.ativoTipo"
            class="ml-4px text-12px text-[var(--el-text-color-secondary)]"
            >{{ rotulo(TIPOS_ATIVO, row.ativoTipo) }}</span
          >
        </template>
      </el-table-column>
      <el-table-column label="Credencial" min-width="130" prop="credencial" />
      <el-table-column label="Última resposta" width="150">
        <template #default="{ row }">{{ quando(row.ultimaResposta) }}</template>
      </el-table-column>
      <el-table-column label="Última tentativa" width="150">
        <template #default="{ row }">{{ quando(row.ultimaTentativa) }}</template>
      </el-table-column>
      <el-table-column label="Erro" min-width="200" prop="erro" show-overflow-tooltip />
    </el-table>
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar credencial' : 'Nova credencial'"
    width="600px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="150px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Versão">
        <el-radio-group v-model="form.dados.versao">
          <el-radio-button value="v2c">v2c</el-radio-button>
          <el-radio-button value="v3">v3</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="form.dados.versao === 'v2c'">
        <el-form-item label="Comunidade">
          <el-input
            v-model="form.dados.comunidade"
            :placeholder="form.dados.comunidadeDefinida ? 'Guardada. Preencha só para trocar' : ''"
            autocomplete="off"
            maxlength="200"
            show-password
          />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="Usuário"
          ><el-input v-model="form.dados.usuario" autocomplete="off" maxlength="100"
        /></el-form-item>
        <el-form-item label="Segurança">
          <el-select v-model="form.dados.nivel" class="w-full">
            <el-option label="Autenticação e criptografia (authPriv)" value="authPriv" />
            <el-option label="Só autenticação (authNoPriv)" value="authNoPriv" />
            <el-option label="Nenhuma (noAuthNoPriv)" value="noAuthNoPriv" />
          </el-select>
        </el-form-item>
        <template v-if="form.dados.nivel !== 'noAuthNoPriv'">
          <el-form-item label="Autenticação">
            <el-select v-model="form.dados.authProtocolo" class="!w-120px">
              <el-option
                v-for="p in ['SHA', 'SHA256', 'SHA512', 'MD5']"
                :key="p"
                :label="p"
                :value="p"
              />
            </el-select>
            <el-input
              v-model="form.dados.authSenha"
              :placeholder="form.dados.authSenhaDefinida ? 'Guardada' : 'Mínimo 8 caracteres'"
              autocomplete="new-password"
              class="!w-240px ml-8px"
              maxlength="200"
              show-password
            />
          </el-form-item>
        </template>
        <template v-if="form.dados.nivel === 'authPriv'">
          <el-form-item label="Criptografia">
            <el-select v-model="form.dados.privProtocolo" class="!w-120px">
              <el-option v-for="p in ['AES', 'AES256', 'DES']" :key="p" :label="p" :value="p" />
            </el-select>
            <el-input
              v-model="form.dados.privSenha"
              :placeholder="form.dados.privSenhaDefinida ? 'Guardada' : 'Mínimo 8 caracteres'"
              autocomplete="new-password"
              class="!w-240px ml-8px"
              maxlength="200"
              show-password
            />
          </el-form-item>
        </template>
      </template>
      <el-form-item label="Porta">
        <el-input-number
          v-model="form.dados.porta"
          :max="65535"
          :min="1"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="Ordem">
        <el-input-number v-model="form.dados.ordem" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="Ativa"><el-switch v-model="form.dados.ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'
import { TIPOS_ATIVO, rotulo } from '@/api/inventario'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'InventarioSnmp' })
const message = useMessage()
const router = useRouter()
const pode = checkPermi(['inventario:snmp:update'])
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }

const credenciais = ref<Inv.CredencialSnmp[]>([])
const alvos = ref<Inv.AlvoSnmp[]>([])
const loading = ref(true)
const ip = ref('')
const consultando = ref(false)
const varrendo = ref(false)
const resultado = ref<Inv.ResultadoSnmp>()
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as any })

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

const carregar = async () => {
  loading.value = true
  try {
    alvos.value = await Inv.getAlvosSnmp()
    if (pode) credenciais.value = await Inv.getCredenciaisSnmp()
  } finally {
    loading.value = false
  }
}

const abrirCredencial = (c?: Inv.CredencialSnmp) => {
  form.dados = c
    ? { ...c, comunidade: '', authSenha: '', privSenha: '' }
    : {
        nome: '',
        versao: 'v2c',
        nivel: 'authPriv',
        authProtocolo: 'SHA',
        privProtocolo: 'AES',
        porta: 161,
        ordem: credenciais.value.length,
        ativo: true
      }
  form.visivel = true
}
const salvar = async () => {
  await formRef.value.validate()
  await Inv.salvarCredencialSnmp(form.dados)
  message.success('Credencial salva')
  form.visivel = false
  await carregar()
}
const excluir = async (c: Inv.CredencialSnmp) => {
  await message.delConfirm(`Excluir a credencial ${c.nome}?`)
  await Inv.excluirCredencialSnmp(c.id!)
  await carregar()
}

const consultar = async () => {
  consultando.value = true
  try {
    resultado.value = await Inv.consultarSnmp(ip.value.trim())
    await carregar()
  } finally {
    consultando.value = false
  }
}
const varrer = async () => {
  varrendo.value = true
  try {
    const r = await Inv.varrerSnmp()
    message.success(
      `${r.alvos} IPs consultados, ${r.responderam} responderam, ${r.gravados} gravados`
    )
    await carregar()
  } finally {
    varrendo.value = false
  }
}

onMounted(carregar)
</script>
