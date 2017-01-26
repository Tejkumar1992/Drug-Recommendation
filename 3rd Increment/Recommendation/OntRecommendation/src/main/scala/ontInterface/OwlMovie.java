package ontInterface;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class OwlMovie {
    OWLOntology ont;
    PrefixManager pm;
    OWLOntologyManager manager;
    OWLDataFactory df;

    public OwlMovie() {
        try {
            pm = new DefaultPrefixManager(null, null, "https://www.kdm.com/OWL/Drug#");
            manager = OWLManager.createOWLOntologyManager();
            df = manager.getOWLDataFactory();
            ont = initialzation();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void createSubClass(String className, String subClassName) {
        OWLClass baseClass = df.getOWLClass(className, pm);
        OWLClass subClass = df.getOWLClass(subClassName, pm);
        OWLSubClassOfAxiom declarationSubClassAxiom = df.getOWLSubClassOfAxiom(subClass, baseClass);
        manager.addAxiom(ont, declarationSubClassAxiom);
    }

    public void createClass(String className) {

        OWLClass classN = df.getOWLClass(className, pm);
        OWLDeclarationAxiom declarationAxiom = df.getOWLDeclarationAxiom(classN);
        manager.addAxiom(ont, declarationAxiom);

    }

    public void createIndividual(String individualName, String className) {
        OWLClass classN = df.getOWLClass(className, pm);
        OWLNamedIndividual ind = df.getOWLNamedIndividual(individualName, pm);
        OWLClassAssertionAxiom classAssertion = df.getOWLClassAssertionAxiom(classN, ind);
        manager.addAxiom(ont, classAssertion);

    }

    public void createObjectProperty(String domain, String propertyName, String range) {

        OWLClass domainC = df.getOWLClass(domain, pm);
        OWLClass rangeC = df.getOWLClass(range, pm);
        OWLObjectProperty isIngredientOf = df.getOWLObjectProperty(propertyName, pm);
        OWLObjectPropertyRangeAxiom rangeAxiom = df.getOWLObjectPropertyRangeAxiom(isIngredientOf, rangeC);
        OWLObjectPropertyDomainAxiom domainAxiom = df.getOWLObjectPropertyDomainAxiom(isIngredientOf, domainC);
        manager.addAxiom(ont, rangeAxiom);
        manager.addAxiom(ont, domainAxiom);

    }

    public OWLOntology initialzation() throws Exception {
        //creating ontology manager
        //In order to create objects that represent entities we need a

        ont = manager.createOntology(IRI.create("https://www.kdm.com/OWL/", "Drug"));

        OWLClass DrugID = df.getOWLClass(":DrugID", pm);
        OWLDeclarationAxiom declarationAxiom3 = df.getOWLDeclarationAxiom(DrugID);
        manager.addAxiom(ont, declarationAxiom3);


        OWLClass Movie = df.getOWLClass(":Drug", pm);
        OWLDeclarationAxiom declarationAxiom1 = df.getOWLDeclarationAxiom(Movie);
        manager.addAxiom(ont, declarationAxiom1);

        OWLClass Genre = df.getOWLClass(":Genre", pm);
        OWLDeclarationAxiom declarationAxiom2 = df.getOWLDeclarationAxiom(Genre);
        manager.addAxiom(ont, declarationAxiom2);

        OWLClass User = df.getOWLClass(":User", pm);
        OWLDeclarationAxiom declarationAxiom4 = df.getOWLDeclarationAxiom(User);
        manager.addAxiom(ont, declarationAxiom4);

        OWLClass Description = df.getOWLClass(":Description", pm);
        OWLDeclarationAxiom declarationAxiom5 = df.getOWLDeclarationAxiom(Description);
        manager.addAxiom(ont, declarationAxiom5);

        //Making all classes Disjoint to each other
        OWLDisjointClassesAxiom disjointClassesAxiom = df.getOWLDisjointClassesAxiom(Movie, Genre, DrugID);
        manager.addAxiom(ont, disjointClassesAxiom);

        return ont;
    }

    public void saveOntology() {
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OutputStream os = new FileOutputStream("data/OwlDrug.owl");
            OWLXMLDocumentFormat owlxmlFormat = new OWLXMLDocumentFormat();
            manager.saveOntology(ont, owlxmlFormat, os);
            System.out.println("Ontology Created");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
