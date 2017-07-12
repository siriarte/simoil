package simoil.estrategias.excavacion;

import simoil.AlquilerRig;
import simoil.EmprendimientoPetrolifero;
import simoil.Parcela;
import simoil.PlanDeExcavacion;

import java.util.ArrayList;


public class EstrategiaExcavacionLoAntesPosible extends EstrategiaExcavacion {

    @Override
    public ArrayList<PlanDeExcavacion> crearPlanesDeExcavacion(ArrayList<Parcela> parcelasDondeExcavar) {
        ArrayList<PlanDeExcavacion> planes = new ArrayList<>();
        for (Parcela parcela : parcelasDondeExcavar) {
            planes.add(new PlanDeExcavacion(0, parcela));
        }
        return planes;
    }

    @Override
    public int cuantosRigsAlquilarSimultaneamente(int maximaCantidadDeRigsSimultaneos) {
        return maximaCantidadDeRigsSimultaneos;
    }

    @Override
    public AlquilerRig dameNuevoAlquilerDeRig(ArrayList<AlquilerRig> catalogoAlquilerRigs, Parcela parcelaDondeExcavar) {
        AlquilerRig alquilerSeleccionado = catalogoAlquilerRigs.get(0);
        for (AlquilerRig alquiler : catalogoAlquilerRigs) {
            if (alquiler.rig().poderExcavacion() > alquilerSeleccionado.rig().poderExcavacion()) {
                alquilerSeleccionado = alquiler;
            }
        }
        return new AlquilerRig(alquilerSeleccionado);
    }

}