package simoil;

import simoil.estrategias.condicionDeFin.EstrategiaCondicionDeFin;
import simoil.estrategias.condicionDeFin.EstrategiaCondicionDeFinPorDilucionCritica;
import simoil.estrategias.construccion.EstrategiaConstruccion;
import simoil.estrategias.construccion.EstrategiaConstruccionPlantaUnica;
import simoil.estrategias.estrategiaVentaGas.EstrategiaVentaGasVenderTodosLosDias;
import simoil.estrategias.excavacion.EstrategiaExcavacion;
import simoil.estrategias.excavacion.EstrategiaExcavacionLoAntesPosible;
import simoil.estrategias.extraccion.EstrategiaExtraccionTodosLosPozosHabilitados;
import simoil.estrategias.reinyeccion.EstrategiaReinyeccionNoReinyectar;
import simoil.estrategias.seleccionParcelas.EstrategiaSeleccionParcelas;
import simoil.estrategias.seleccionParcelas.EstrategiaSeleccionParcelasPorMaximaPresion;

import java.util.ArrayList;
import java.util.Iterator;

public class Simulador {
    private int diaActual = 1;
    private Logger logger = new Logger();
    private ArrayList<ConexionEntreEstructuras> conexionesPendientesPozoPlantaProcesadora = new ArrayList<>();
    private ArrayList<ConexionEntreEstructuras> conexionesPendientesPlantaProcesadoraTanqueAgua = new ArrayList<>();
    private ArrayList<ConexionEntreEstructuras> conexionesPendientesPlantaProcesadoraTanqueGas = new ArrayList<>();
    private ArrayList<Excavacion> excavacionesPendientesDeFinalizacion = new ArrayList<>();
    private int maximoDias;
    private int maximaCantidadRigsSimultaneos;
    private float volumenMaximoReinyeccionEnUnDia;
    private float porcentajeCriticoPetroleo;
    private int cantidadDePozosDeseados;
    private float precioLitroDeCombustibleRig;
    private float precioLitroGas;
    private float precioLitroPetroleo;
    private float precioLitroAguaEspecialComprada;
    private EmprendimientoPetrolifero emprendimientoPetrolifero;
    private ArrayList<Parcela> parcelasSeleccionadasDondeExcavar;

    public Simulador(int maximoDias, int maximaCantidadRigsSimultaneos, float volumenMaximoReinyeccionEnUnDia, float porcentajeCriticoPetroleo, int cantidadDePozosDeseados, float precioLitroDeCombustibleRig, float precioLitroGas, float precioLitroPetroleo, float precioLitroAguaEspecialComprada, EmprendimientoPetrolifero emprendimientoPetrolifero) {
        this.maximoDias = maximoDias;
        this.maximaCantidadRigsSimultaneos = maximaCantidadRigsSimultaneos;
        this.volumenMaximoReinyeccionEnUnDia = volumenMaximoReinyeccionEnUnDia;
        this.porcentajeCriticoPetroleo = porcentajeCriticoPetroleo;
        this.cantidadDePozosDeseados = cantidadDePozosDeseados;
        this.precioLitroDeCombustibleRig = precioLitroDeCombustibleRig;
        this.precioLitroGas = precioLitroGas;
        this.precioLitroPetroleo = precioLitroPetroleo;
        this.precioLitroAguaEspecialComprada = precioLitroAguaEspecialComprada;
        this.emprendimientoPetrolifero = emprendimientoPetrolifero;
    }

    public void iniciarSimulacion() {
        // Primero necesitamos conocer las parcelas donde vamos a excavar los pozos
        EstrategiaSeleccionParcelas estrategiaSeleccionParcelas = emprendimientoPetrolifero.equipoDeIngenieria().estrategiaSeleccionParcelas();
        this.parcelasSeleccionadasDondeExcavar = estrategiaSeleccionParcelas.seleccionarParcelasParaExcavar(emprendimientoPetrolifero, cantidadDePozosDeseados);

        EstrategiaCondicionDeFin estrategiaCondicionDeFin = emprendimientoPetrolifero.equipoDeIngenieria().estrategiaCondicionDeFin();
        while (!estrategiaCondicionDeFin.hayQueFinalizar(emprendimientoPetrolifero, diaActual, maximoDias, porcentajeCriticoPetroleo)) {
            simularUnDia();
        }
    }

    private void simularUnDia() {
        logger.log("Dia " + diaActual + ":");

        // Definimos los proyectos de construccion de plantas procesadoras y tanques
        definirNuevosProyectosConstruccion();

        // Definimos nuevas excavaciones
        definirNuevasExcavaciones();

        // Primero tenemos que decidir si vamos a vender el gas que almacenamos anteriormente
        if (emprendimientoPetrolifero.equipoDeIngenieria().estrategiaVentaGas().hayQueVenderElGas(emprendimientoPetrolifero)) {
            venderGas();
        }

        // Ahora hay decidir si reinyectar o extraer producto
        if (emprendimientoPetrolifero.equipoDeIngenieria().estrategiaReinyeccion().hayQueReinyectar(emprendimientoPetrolifero)) {
            // Hay que reinyectar, hoy no podemos extraer de ningun pozo
            reinyectar();
        } else { // Si no se reinyecta, se intenta extraer
            extraerProducto();
        }

        // Avanzamos con las construcciones de tanques y plantas procesadoras
        construir();

        // Avanzamos con las excavaciones de pozos
        excavar();

        // Finalmente cerramos el dia: Apagamos Rigs y plantas procesadoras, cerramos valvulas de los pozos, etc.
        finalizarDia();
    }

    private void definirNuevosProyectosConstruccion() {
        EstrategiaConstruccion estrategiaConstruccion = emprendimientoPetrolifero.equipoDeIngenieria().estrategiaConstruccion();
        estrategiaConstruccion.crearProyectos(emprendimientoPetrolifero);
        ArrayList<ProyectoConstruccionPlantaProcesadora> proyectosNuevosPlantas = estrategiaConstruccion.nuevosProyectosConstruccionPlantas();
        for (ProyectoConstruccionPlantaProcesadora proyectoPlanta : proyectosNuevosPlantas) {
            emprendimientoPetrolifero.agregarProyectoDePlantaProcesadora(proyectoPlanta);
            emprendimientoPetrolifero.registroContable().sumarGasto(proyectoPlanta.especificacionPlantaProcesadora().costo());
            logger.log("Se proyecto la contruccion de la planta procesadora " + proyectoPlanta.nombrePlantaEnConstruccion() + ". Su construccion comienza el dia " +
                    proyectoPlanta.diaComienzoConstruccion() + " y su costo fue de $" + proyectoPlanta.especificacionPlantaProcesadora().costo() + ".");
        }
        ArrayList<ProyectoConstruccionTanque> proyectosNuevosTanquesDeAgua = estrategiaConstruccion.nuevosProyectosConstruccionTanquesDeAgua();
        for (ProyectoConstruccionTanque proyectoTanqueAgua : proyectosNuevosTanquesDeAgua) {
            emprendimientoPetrolifero.agregarProyectoDeTanqueDeAgua(proyectoTanqueAgua);
            emprendimientoPetrolifero.registroContable().sumarGasto(proyectoTanqueAgua.especificacionTanque().costo());
            logger.log("Se proyecto la contruccion del tanque de agua " + proyectoTanqueAgua.nombreTanqueEnConstruccion() + ". Su construccion comienza el dia " +
                    proyectoTanqueAgua.diaComienzoConstruccion() + " y su costo fue de $" + proyectoTanqueAgua.especificacionTanque().costo() + ".");
        }
        ArrayList<ProyectoConstruccionTanque> proyectosNuevosTanquesDeGas = estrategiaConstruccion.nuevosProyectosConstruccionTanquesDeGas();
        for (ProyectoConstruccionTanque proyectoTanqueGas : proyectosNuevosTanquesDeGas) {
            emprendimientoPetrolifero.agregarProyectoDeTanqueDeGas(proyectoTanqueGas);
            emprendimientoPetrolifero.registroContable().sumarGasto(proyectoTanqueGas.especificacionTanque().costo());
            logger.log("Se proyecto la contruccion del tanque de gas " + proyectoTanqueGas.nombreTanqueEnConstruccion() + ". Su construccion comienza el dia " +
                    proyectoTanqueGas.diaComienzoConstruccion() + " y su costo fue de $" + proyectoTanqueGas.especificacionTanque().costo() + ".");
        }

        ArrayList<ConexionEntreEstructuras> nuevasConexionesPlantaTanqueAgua = estrategiaConstruccion.nuevasConexionesPlantaTanqueAgua();
        conexionesPendientesPlantaProcesadoraTanqueAgua.addAll(nuevasConexionesPlantaTanqueAgua);

        ArrayList<ConexionEntreEstructuras> nuevasConexionesPlantaTanqueGas = estrategiaConstruccion.nuevasConexionesPlantaTanqueGas();
        conexionesPendientesPlantaProcesadoraTanqueGas.addAll(nuevasConexionesPlantaTanqueGas);
    }

    private void definirNuevasExcavaciones() {
        EstrategiaExcavacion estrategiaExcavacion = emprendimientoPetrolifero.equipoDeIngenieria().estrategiaExcavacion();
        ArrayList<Excavacion> nuevasExcavaciones = estrategiaExcavacion.crearExcavaciones(emprendimientoPetrolifero, parcelasSeleccionadasDondeExcavar);
        for (Excavacion nuevaExcavacion : nuevasExcavaciones) {
            logger.log("Se creo la excavacion del pozo " + nuevaExcavacion.nombrePozoEnExcavacion() + ".");
            emprendimientoPetrolifero.agregarExcavacion(nuevaExcavacion);
        }
        conexionesPendientesPozoPlantaProcesadora.addAll(estrategiaExcavacion.nuevasConexionesDePozoAPlantas(emprendimientoPetrolifero));
    }

    private void extraerProducto() {
        emprendimientoPetrolifero.equipoDeIngenieria().estrategiaExtraccion().abrirValvulasDePozos(emprendimientoPetrolifero);
        Yacimiento yacimiento = emprendimientoPetrolifero.yacimiento();
        ComposicionDeProducto composicionDeProducto = yacimiento.composicionDeProducto();
        float volumenPetroleoExtraidoEnElDia = 0;
        float proporcionPetroleo = yacimiento.composicionDeProducto().porcentajePetroleo() / 100f;
        for (Pozo pozo : yacimiento.pozosHabilitadosParaExtraccion()) {
            if (pozo.valvulaPrincipalAbierta()) {
                float volumenPotencialDelPozo = yacimiento.volumenPotencialDiarioPozo(pozo);
                logger.log("El pozo " + pozo.nombre() + " puede extraer potencialmente " + volumenPotencialDelPozo + " litros de producto.");
                float volumenAExtraerDelPozo = 0;
                // Tenemos que definir cuantos litros podemos realmente extraer segun plantas procesadoras conectadas al pozo
                for (PlantaProcesadora plantaProcesadora : pozo.plantasProcesadorasConectadas()) {
                    if (volumenPotencialDelPozo <= 0) {
                        break;
                    }
                    // Chequeamos que la planta procesadora conectada esta efectivamente habilitada en el yacimiento
                    if (emprendimientoPetrolifero.plantasProcesadorasHabilitadas().contains(plantaProcesadora)) {
                        float volumenProcesado = plantaProcesadora.procesarProducto(volumenPotencialDelPozo, emprendimientoPetrolifero);
                        if (volumenProcesado > 0) {
                            logger.log("La planta procesadora " + plantaProcesadora.nombre + " proceso " + volumenProcesado + " litros de producto.");
                        } else {
                            logger.log("La planta procesadora " + plantaProcesadora.nombre + " no puede procesar mas producto.");
                        }
                        volumenPotencialDelPozo -= volumenProcesado;
                        volumenAExtraerDelPozo += volumenProcesado;
                    }
                }
                if (Math.abs(volumenAExtraerDelPozo - yacimiento.extraerProducto(volumenAExtraerDelPozo)) > 0.1) {
                    throw new RuntimeException("El volumen que se extrajo del yacimiento no es el esperado.");
                }
                volumenPetroleoExtraidoEnElDia += volumenAExtraerDelPozo * proporcionPetroleo;
                if (volumenAExtraerDelPozo > 0) {
                    logger.log("El pozo " + pozo.nombre() + " extrajo " + volumenAExtraerDelPozo + " litros de producto del yacimiento.");
                }
            }
        }
        if (volumenPetroleoExtraidoEnElDia > 0) {
            float ingresoPorPetroleo = volumenPetroleoExtraidoEnElDia * precioLitroPetroleo;
            emprendimientoPetrolifero.registroContable().sumarIngreso(ingresoPorPetroleo);
            logger.log("Se vendieron " + volumenPetroleoExtraidoEnElDia + " litros de petroleo por $" + ingresoPorPetroleo + ".");
        }
        if (volumenPetroleoExtraidoEnElDia > 0) {
            yacimiento.actualizarPresionesPozos();
        }
    }

    private void reinyectar() {
        //TODO COMPLETAR
    }

    private void finalizarDia() {
        // Cerramos valvulas principales de todos los pozos
        for (Pozo pozo : emprendimientoPetrolifero.yacimiento().pozosHabilitadosParaExtraccion())
            pozo.cerrarValvulaPrincipal();
        // Apagamos Rigs
        for (AlquilerRig alquilerRig : emprendimientoPetrolifero.alquileresDeRigsContratados())
            alquilerRig.rig().apagar();
        // Apagamos plantas procesadoras
        for (PlantaProcesadora plantaProcesadora : emprendimientoPetrolifero.plantasProcesadorasHabilitadas())
            plantaProcesadora.apagar();
        // Creamos los pozos que quedaron pendientes por no tener su planta procesadora construida
        habilitarPozosPendientes();
        // Conectamos lo que podamos
        conectarEstructuras();
        // Logueamos informacion contable
        float ganancia = emprendimientoPetrolifero.registroContable().ganancia();
        logger.log(
                "Total ingresos: $" + emprendimientoPetrolifero.registroContable().ingresos() +
                        ". Total gastos: $" + emprendimientoPetrolifero.registroContable().gastos() +
                        ". Ganancia: " + (ganancia < 0 ? "-$" : "$") + Math.abs(ganancia) + ".\n");
        diaActual++;
    }

    private void habilitarPozosPendientes() {
        Iterator<Excavacion> itExcavacionPendiente = excavacionesPendientesDeFinalizacion.iterator();
        while (itExcavacionPendiente.hasNext()) {
            Excavacion excavacionPendiente = itExcavacionPendiente.next();
            String nombrePozo = excavacionPendiente.nombrePozoEnExcavacion();
            ArrayList<PlantaProcesadora> plantasDondeConectar = new ArrayList<>();
            Iterator<ConexionEntreEstructuras> itConexionesPozoPlanta = conexionesPendientesPozoPlantaProcesadora.iterator();
            while (itConexionesPozoPlanta.hasNext()) {
                ConexionEntreEstructuras conexion = itConexionesPozoPlanta.next();
                String nombrePlanta = conexion.nombreEstructuraDestino();
                if (conexion.nombreEstructuraOrigen().equals(nombrePozo) &&
                        emprendimientoPetrolifero.plantaProcesadoraHabilitada(nombrePlanta)) {
                    itExcavacionPendiente.remove();
                    itConexionesPozoPlanta.remove();
                    plantasDondeConectar.add(emprendimientoPetrolifero.plantaProcesadoraPorNombre(nombrePlanta));
                }
            }
            if (plantasDondeConectar.size() > 0) {
                Parcela parcelaDelPozo = excavacionPendiente.parcelaEnExcavacion();
                parcelaDelPozo.habilitarPozo(plantasDondeConectar.get(0));
                Pozo nuevoPozo = parcelaDelPozo.pozo();
                logger.log("El pozo " + nuevoPozo.nombre() + " fue habilitado para la extraccion.");
                for (int i = 1; i < plantasDondeConectar.size(); i++)
                    nuevoPozo.conectarPlantaProcesadora(plantasDondeConectar.get(i));
            }
        }
    }

    private void conectarEstructuras() {
        // Intentamos conectar pozos con plantas procesadoras
        Iterator<ConexionEntreEstructuras> itPozoPlanta = conexionesPendientesPozoPlantaProcesadora.iterator();
        while (itPozoPlanta.hasNext()) {
            ConexionEntreEstructuras conexionPozoPlanta = itPozoPlanta.next();
            String nombrePozo = conexionPozoPlanta.nombreEstructuraOrigen();
            String nombrePlantaProcesadora = conexionPozoPlanta.nombreEstructuraDestino();
            if (emprendimientoPetrolifero.pozoHabilitado(nombrePozo) &&
                    emprendimientoPetrolifero.plantaProcesadoraHabilitada(nombrePlantaProcesadora)) {
                itPozoPlanta.remove();
                emprendimientoPetrolifero.yacimiento().pozoPorNombre(nombrePozo).conectarPlantaProcesadora(
                        emprendimientoPetrolifero.plantaProcesadoraPorNombre(nombrePlantaProcesadora)
                );
                logger.log("Pozo " + nombrePozo + " conectado con planta " + nombrePlantaProcesadora + ".");
            }
        }
        // Intentamos conectar plantas procesadoras con tanques
        Iterator<ConexionEntreEstructuras> itPlantaTanqueAgua = conexionesPendientesPlantaProcesadoraTanqueAgua.iterator();
        while (itPlantaTanqueAgua.hasNext()) {
            ConexionEntreEstructuras conexionPlantaTanqueAgua = itPlantaTanqueAgua.next();
            String nombrePlantaProcesadora = conexionPlantaTanqueAgua.nombreEstructuraOrigen();
            String nombreTanqueAgua = conexionPlantaTanqueAgua.nombreEstructuraDestino();
            if (emprendimientoPetrolifero.plantaProcesadoraHabilitada(nombrePlantaProcesadora) &&
                    emprendimientoPetrolifero.tanqueDeAguaHabilitado(nombreTanqueAgua)) {
                itPlantaTanqueAgua.remove();
                emprendimientoPetrolifero.plantaProcesadoraPorNombre(nombrePlantaProcesadora).conectarTanqueDeAgua(
                        emprendimientoPetrolifero.tanqueDeAguaPorNombre(nombreTanqueAgua)
                );
            }
        }
        Iterator<ConexionEntreEstructuras> itPlantaTanqueGas = conexionesPendientesPlantaProcesadoraTanqueGas.iterator();
        while (itPlantaTanqueGas.hasNext()) {
            ConexionEntreEstructuras conexionPlantaTanqueGas = itPlantaTanqueGas.next();
            String nombrePlantaProcesadora = conexionPlantaTanqueGas.nombreEstructuraOrigen();
            String nombreTanqueGas = conexionPlantaTanqueGas.nombreEstructuraDestino();
            if (emprendimientoPetrolifero.plantaProcesadoraHabilitada(nombrePlantaProcesadora) &&
                    emprendimientoPetrolifero.tanqueDeGasHabilitado(nombreTanqueGas)) {
                itPlantaTanqueGas.remove();
                emprendimientoPetrolifero.plantaProcesadoraPorNombre(nombrePlantaProcesadora).conectarTanqueDeGas(
                        emprendimientoPetrolifero.tanqueDeGasPorNombre(nombreTanqueGas)
                );
            }
        }
    }

    private void construir() {
        Iterator<ProyectoConstruccionPlantaProcesadora> itProyectoPlanta = emprendimientoPetrolifero.proyectosDePlantasProcesadoras().iterator();
        while (itProyectoPlanta.hasNext()) {
            ProyectoConstruccionPlantaProcesadora proyectoConstruccionPlanta = itProyectoPlanta.next();
            if (proyectoConstruccionPlanta.diaComienzoConstruccion() <= diaActual && proyectoConstruccionPlanta.construirUnDia()) {
                itProyectoPlanta.remove();
                PlantaProcesadora nuevaPlanta = new PlantaProcesadora(
                        proyectoConstruccionPlanta.nombrePlantaEnConstruccion(),
                        proyectoConstruccionPlanta.especificacionPlantaProcesadora().capacidadProcesamientoTotal()
                );
                emprendimientoPetrolifero.habilitarPlantaProcesadora(nuevaPlanta);
                logger.log("La construccion de la planta procesadora " + nuevaPlanta.nombre() + " finalizo hoy.");
            }
        }
        Iterator<ProyectoConstruccionTanque> itProyectoTanqueAgua = emprendimientoPetrolifero.proyectosDeTanquesDeAgua().iterator();
        while (itProyectoTanqueAgua.hasNext()) {
            ProyectoConstruccionTanque proyectoConstruccionTanqueAgua = itProyectoTanqueAgua.next();
            if (proyectoConstruccionTanqueAgua.diaComienzoConstruccion() <= diaActual && proyectoConstruccionTanqueAgua.construirUnDia()) {
                itProyectoTanqueAgua.remove();
                Tanque nuevoTanqueAgua = new Tanque(
                        proyectoConstruccionTanqueAgua.nombreTanqueEnConstruccion(),
                        proyectoConstruccionTanqueAgua.especificacionTanque().capacidadTotal()
                );
                emprendimientoPetrolifero.habilitarTanqueDeAgua(nuevoTanqueAgua);
                logger.log("La construccion del tanque de agua " + nuevoTanqueAgua.nombre() + " finalizo hoy.");
            }
        }
        Iterator<ProyectoConstruccionTanque> itProyectoTanqueGas = emprendimientoPetrolifero.proyectosDeTanquesDeGas().iterator();
        while (itProyectoTanqueGas.hasNext()) {
            ProyectoConstruccionTanque proyectoConstruccionTanqueGas = itProyectoTanqueGas.next();
            if (proyectoConstruccionTanqueGas.diaComienzoConstruccion() <= diaActual && proyectoConstruccionTanqueGas.construirUnDia()) {
                itProyectoTanqueGas.remove();
                Tanque nuevoTanqueGas = new Tanque(
                        proyectoConstruccionTanqueGas.nombreTanqueEnConstruccion(),
                        proyectoConstruccionTanqueGas.especificacionTanque().capacidadTotal()
                );
                emprendimientoPetrolifero.habilitarTanqueDeGas(nuevoTanqueGas);
                logger.log("La construccion del tanque de gas " + nuevoTanqueGas.nombre() + "finalizo hoy.");
            }
        }
    }

    private void excavar() {
        EstrategiaExcavacion estrategiaExcavacion = emprendimientoPetrolifero.equipoDeIngenieria().estrategiaExcavacion();
        Iterator<Excavacion> itExcavacion = emprendimientoPetrolifero.excavaciones().iterator();
        Iterator<AlquilerRig> itAlquilerRigContratado = emprendimientoPetrolifero.alquileresDeRigsContratados().iterator();
        ArrayList<AlquilerRig> nuevosAlquileres = new ArrayList<>();
        while (itExcavacion.hasNext()) {
            Excavacion excavacion = itExcavacion.next();
            if (excavacion.diaDeComienzoDeExcavacion() <= diaActual) {
                Rig rig = null;
                if (itAlquilerRigContratado.hasNext()) {
                    AlquilerRig alquilerRig = itAlquilerRigContratado.next();
                    rig = alquilerRig.rig();
                } else {
                    int cantidadRigsAlquilados = emprendimientoPetrolifero.alquileresDeRigsContratados().size();
                    if (cantidadRigsAlquilados < estrategiaExcavacion.cuantosRigsAlquilarSimultaneamente(maximaCantidadRigsSimultaneos)
                            && emprendimientoPetrolifero.catalogoAlquileresRigs().size() > 0) {
                        AlquilerRig nuevoAlquiler = estrategiaExcavacion.elegirUnNuevoAlquilerDeRig(emprendimientoPetrolifero, excavacion);
                        nuevosAlquileres.add(nuevoAlquiler);
                        rig = nuevoAlquiler.rig();
                        logger.log("Se alquilo el rig " + rig.nombre() + ".");
                    }
                }
                if (rig != null) {
                    float costoCombustibleConsumido = rig.consumoCombustibleDiarioEnLitros() * precioLitroDeCombustibleRig;
                    emprendimientoPetrolifero.registroContable().sumarGasto(costoCombustibleConsumido);
                    float metrosExcavados = rig.excavar(excavacion);
                    logger.log("El rig " + rig.nombre() + " excavo " + metrosExcavados + " metros en la excavacion del pozo " + excavacion.nombrePozoEnExcavacion() + ".");
                    if (excavacion.excavacionFinalizada()) {
                        itExcavacion.remove();
                        logger.log("Se finalizo la excavacion del pozo " + excavacion.nombrePozoEnExcavacion() + ".");
                        excavacionesPendientesDeFinalizacion.add(excavacion);
                    }
                }
            }
        }
        while (itAlquilerRigContratado.hasNext()) { // Si hubo Rigs que no se usaron, intentamos cancelar el alquiler
            AlquilerRig alquilerRig = itAlquilerRigContratado.next();
            if (alquilerRig.diasAlquilado() >= alquilerRig.minimoDias()) {
                itAlquilerRigContratado.remove();
                // Lo volvemos a agregar a la lista de alquileres que podemos contratar
                alquilerRig.finalizarAlquiler();
                emprendimientoPetrolifero.catalogoAlquileresRigs().add(alquilerRig);
            }
        }
        for (AlquilerRig nuevoAlquiler : nuevosAlquileres) {
            emprendimientoPetrolifero.contratarAlquilerDeRig(nuevoAlquiler);
        }
        float costoTotalAlquileresHoy = 0;
        for (AlquilerRig alquilerRig : emprendimientoPetrolifero.alquileresDeRigsContratados()) {
            costoTotalAlquileresHoy += alquilerRig.costoDiario();
            alquilerRig.avanzarUnDia();
        }
        if (costoTotalAlquileresHoy > 0)
            logger.log("Hoy se gasto $" + costoTotalAlquileresHoy + " en alquileres de Rigs.");
        emprendimientoPetrolifero.registroContable().sumarGasto(costoTotalAlquileresHoy);
    }

    private void venderGas() {
        float totalLitrosDeGasVendidos = 0;
        ArrayList<Tanque> tanquesDeGas = emprendimientoPetrolifero.tanquesDeGasHabilitados();
        for (Tanque tanqueGas : tanquesDeGas) {
            totalLitrosDeGasVendidos += tanqueGas.descargarTodo();
        }
        float precioDelGasVendido = totalLitrosDeGasVendidos * precioLitroGas;
        emprendimientoPetrolifero.registroContable().sumarIngreso(precioDelGasVendido);
        if (precioDelGasVendido > 0)
            logger.log("Se vendieron " + totalLitrosDeGasVendidos + " litros de gas por $" + precioDelGasVendido + ".");
    }

    public static void main(String[] args) {
        TipoTerreno rocoso = new TipoTerreno("rocoso", 60);
        TipoTerreno arcilloso = new TipoTerreno("arcilloso", -10);
        ArrayList<Parcela> parcelas = new ArrayList<>();
        parcelas.add(new Parcela("1", rocoso, 10, 3100));
        parcelas.add(new Parcela("2", rocoso, 20, 3200));
        parcelas.add(new Parcela("3", arcilloso, 30, 3300));
        Yacimiento yacimiento = new Yacimiento(
                0.1f,
                0.01f,
                10000000,
                10000000,
                80000000,
                parcelas);

        ArrayList<EspecificacionPlantaProcesadora> catalogoPlantas = new ArrayList<>();
        catalogoPlantas.add(new EspecificacionPlantaProcesadora(3, 200, 20000));
        catalogoPlantas.add(new EspecificacionPlantaProcesadora(5, 1000, 50000));
        ArrayList<EspecificacionTanque> catalogoTanques = new ArrayList<>();
        catalogoTanques.add(new EspecificacionTanque(2, 100, 30000));
        catalogoTanques.add(new EspecificacionTanque(3, 150, 50000));
        ArrayList<AlquilerRig> catalogoAlquileresRigs = new ArrayList<>();
        catalogoAlquileresRigs.add(new AlquilerRig(60, 3, new Rig("1", 2, 10)));
        catalogoAlquileresRigs.add(new AlquilerRig(100, 5, new Rig("2", 4, 15)));

        EquipoDeIngenieria equipo = new EquipoDeIngenieria(new EstrategiaSeleccionParcelasPorMaximaPresion(), new EstrategiaExcavacionLoAntesPosible(), new EstrategiaConstruccionPlantaUnica(), new EstrategiaExtraccionTodosLosPozosHabilitados(), new EstrategiaReinyeccionNoReinyectar(), new EstrategiaCondicionDeFinPorDilucionCritica(), new EstrategiaVentaGasVenderTodosLosDias());
        EmprendimientoPetrolifero emprendimiento = new EmprendimientoPetrolifero(yacimiento, equipo, catalogoPlantas, catalogoTanques, catalogoAlquileresRigs);
        Simulador sim = new Simulador(
                25,
                2,
                1000,
                35,
                3,
                10,
                0.1f,
                0.22f,
                0.03f,
                emprendimiento);

        sim.iniciarSimulacion();
    }
}
